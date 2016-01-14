import re
import threading
from pathlib import Path

from queue import Queue
from sqlalchemy import *
import requests
import time

# DB connection
engine = create_engine("mysql+mysqldb://bdr:bdr@127.0.0.1:3307/bdr")
db = engine.connect()

query = """
	SELECT identifier AS id
	FROM complete_cards
	WHERE version NOT LIKE "%%R" COLLATE utf8_bin AND version NOT IN ('S', 'SP', 'SS', 'SSP', 'X')
"""

imgs = Path("images")
if not imgs.exists():
	imgs.mkdir()

queue = Queue()
i = 0


class Thread(threading.Thread):
	def run(self):
		while not queue.empty():
			id = queue.get()
			file = (imgs / (id + ".gif"))
			if not file.exists():
				res = requests.get("http://ws-tcg.com/cardlist/cardimages/{}.gif".format(id))
				file.write_bytes(res.content)
			else:
				bytes = file.read_bytes()
				if not bytes.startswith(b'GIF'):
					old_id = id
					id = re.sub('_([0-9])', "_0\\1", id).replace("_", "-")
					print(old_id, id)
					res = requests.get("http://www.heartofthecards.com/images/cards/ws/{}.gif".format(id))
					file.write_bytes(res.content)


for (id,) in db.execute(query):
	queue.put(id.replace("/", "_").replace("-", "_").lower())

for i in [1, 2, 3, 4, 5, 6, 7, 8]:
	thread = Thread()
	thread.start()
