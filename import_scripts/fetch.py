import re
from pathlib import Path
from time import time, sleep
import hashlib

import requests


def hash_url(url):
	return hashlib.sha1(url.encode()).hexdigest()


# Fetch URL with cache
def fetch(url):
	# Compute hash key from URL
	key = hash_url(url)

	# Create cache directory if it does not exists
	cache_dir = Path("cache")
	if not cache_dir.exists():
		cache_dir.mkdir()

	# Check cache file status
	file = cache_dir / key
	if not file.exists() or (time() - file.stat().st_mtime) > (60 * 60):
		res = requests.get(url)

		if res.status_code != 200:
			raise Exception("Failed to fetch: " + url)
		else:
			data = res.text

		with file.open("w", encoding="utf-8") as fd:
			fd.write(data)
			return data
	else:
		return file.open("r").read()


def clear_cache(url):
	cache_dir = Path("cache")
	if cache_dir.exists():
		file = cache_dir / hash_url(url)
		if file.exists():
			file.unlink()


def main():
	# Update the decks list
	print("Loading decks lists...")
	html = fetch("http://www.heartofthecards.com/code/cardlist.html?pagetype=ws")

	# Find deck pages links
	decks = re.findall(r'<a href="(/code/.+?cardset=.+?)">(.+?)</a>', html)
	print("Found {} deck pages!".format(len(decks)))

	decks_dir = Path("decks")
	if not decks_dir.exists():
		decks_dir.mkdir()

	for (deck_path, title) in decks:
		print("Importing {}...".format(title))
		url = "http://www.heartofthecards.com" + deck_path

		html = fetch(url)
		res = re.search(r'<a href="(/translations/(.+?\.txt))"', html)
		if res is None:
			print("Failed to find translation link!")
			clear_cache(url)
		else:
			(path, filename) = res.groups()
			url = "http://www.heartofthecards.com" + path
			with (decks_dir / filename).open("w") as fd:
				fd.write(fetch(url))
			with (decks_dir / filename.replace(".txt", ".html")).open("w") as fd:
				fd.write(html)


if __name__ == "__main__":
	main()
