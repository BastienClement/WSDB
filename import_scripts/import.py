import re
from collections import OrderedDict
from functools import reduce
from pathlib import Path
from typing import Dict

from sqlalchemy import *
from sqlalchemy.orm import sessionmaker

# Multi-bytes characters
mb_equiv = {
	"Ｒ": "R", "Ｃ": "C", "Ｔ": "T", "Ｄ": "D", "Ｕ": "U", "ｒ": "r", "２": "2", "Ｓ": "S", "Ｐ": "P", "”": '"', "“": '"'
}

mb_ignore = [
	"…", "ö", "」", "é", "ï", "β", "рукопожатие", "спасибо", "Верный", "「", "Спасибо", "μ", "ｌ", "♡", "ṇ", "†"
]

html_decode = {
	"&#47;": "/", "&#40;": "(", "&#41;": ")", "&#39;": "'"
}

# Cards list
cards = []

# Trigger fix list
trigger_fix = {
	"Soul Bounce": "Return",
	"Soul Shot": "Shot",
	"Soul Gate": "Gate",
	"Salvage": "Comeback",
	"Stock": "Pool"
}

# Enums tables
aspects = {}
colors = {}
rarities = {}
triggers = {}
types = {}
universes = {}
extensions = {}
traits = {}

# DB connection
engine = create_engine("mysql+mysqldb://bdr:bdr@127.0.0.1/bdr")
db = engine.connect()
metadata = MetaData()

Cards = Table("cards", metadata,
	Column("extension", Integer, primary_key=True),
	Column("id", String(5), primary_key=True),
	Column("version", String(5), primary_key=True),
	Column("name", String(64), nullable=False),
	Column("ability", Text),
	Column("flavor", Text),
	Column("rarity", String(3), nullable=False),
	Column("type", String(2), nullable=False),
	Column("color", String(1), nullable=False),
	Column("trigger", String(1), nullable=False),
	Column("level", Integer),
	Column("cost", Integer),
	Column("power", Integer),
	Column("soul", Integer),
	Column("trait1", Integer, nullable=False),
	Column("trait2", Integer, nullable=False),
)


class SilentException(Exception):
	pass


# Enumerate the values of a card attribute
def count_values(cards_list, key):
	o = {}
	for card in cards_list:
		val = card[key]
		if val in o:
			o[val] += 1
		else:
			o[val] = 1
	return o


# Parse a card text to a dictionary
def parse_card(deck, data: str):
	try:
		[name, jp_name, *_] = data.split("\n")
		card_no, rarity = re.search(r'Card No\.: (.+[^\s])\s+Rarity: ([^\s]+)', data).groups()
		color, side, card_type = re.search(r'Color: ([^\s]+)\s+Side: ([^\s]+)\s+([^\s]+)', data).groups()
		level, cost, power, soul = \
			re.search(r'Level: ([^\s]+)\s+Cost: ([^\s]+)\s+Power: ([^\s]+)\s+Soul: ([^\s]+)', data).groups()
		trait1, _, trait2, _ = \
			re.search(r'Trait 1: (?:[^\s]+ \((.*?)\)|([^\s]+))\s+Trait 2: (?:[^\s]+ \((.*?)\)|([^\s]+))', data).groups()
		trigger = re.search(r'Triggers: (.+[^\s])\s*$', data, re.MULTILINE).group(1)
		flavor = re.search(r'Flavor: (.*)\nTEXT:', data, re.DOTALL).group(1).replace("\n", " ")
		card_text = re.search(r'TEXT:\s*(.*)', data, re.DOTALL).group(1)
		universe, extension, full_id, version = re.match(r'(.+)/(.+)\-(.+[0-9])(.*)?', card_no).groups()
	except Exception:
		print("*********\nFAILED ON:\n" + data)
		raise

	card = OrderedDict()
	card["name"] = name
	card["jp_name"] = jp_name
	card["universe"] = universe
	card["extension"] = extension
	card["id"] = full_id
	card["version"] = version.strip() if version != "" else ""
	card["card_no"] = card_no
	card["rarity"] = rarity
	card["color"] = color
	card["side"] = side
	card["card_type"] = card_type
	card["level"] = level
	card["cost"] = cost
	card["power"] = power
	card["soul"] = soul
	card["trait1"] = trait1 if trait1 is not None else "-"
	card["trait2"] = trait2 if trait2 is not None else "-"
	card["trigger"] = trigger_fix[trigger] if trigger in trigger_fix else trigger
	card["flavor"] = flavor
	card["text"] = card_text,
	card["deck"] = deck
	card["primary_id"] = "{}/{}-{}".format(universe, extension, full_id)

	for key in card:
		val = card[key]
		if val is None:
			raise Exception("Found a None value [" + card_no + " " + name + "]: \n" + key + " -> None")
		if type(val) == str and key != "jp_name":
			check_val = reduce(lambda x, y: x.replace(y, ""), mb_ignore, val)
			if len(check_val.encode("utf8")) != len(check_val):
				raise Exception(
						"Found a multi-bytes character [" + card_no + " " + name + "]: \n" + val +
						"\n" + str(val.encode("utf8")))

	return card


# Import a deck from source file
def import_deck(file: Path):
	if not file.exists():
		raise Exception("File '{}' does not exists".format(str(file)))

	data = file.read_text()

	title = re.search(r'^([^\n]+) Translation\n', data).group(1)
	title = reduce(lambda x, y: x.replace(y, html_decode[y]), html_decode, title)
	if re.match(r'.*&#.*', title):
		raise Exception("Found non-decoded HTML code: " + title)
	print("-> Loading '{}'".format(title))

	data = reduce(lambda x, y: x.replace(y, mb_equiv[y]), mb_equiv, data)
	deck_cards = re.findall(r'={10}\s+(.+?)\s+={10}', data, re.DOTALL)

	for card in deck_cards:
		card_data = parse_card(title, card)
		cards.append(card_data)


# Load an enumeration table
def load_table(table_name, columns, target, label = None):
	if label is None:
		label = table_name
	for (key, value) in db.execute("SELECT {} FROM {}".format(columns, table_name)):
		target[key] = value
	print("-> Loaded {} {}".format(len(target), label))


def print_dict(dictionary: Dict):
	for key in dictionary:
		value = dictionary[key]
		if value is None:
			value = ":None:"
		print("  {:15s}  {}".format(key, value))


def card_equals(c1, c2):
	ignore = { "card_no": True }
	for k in c1:
		if k not in ignore and (k not in c2 or c1[k] != c2[k]):
			return False
	for k in c2:
		if k not in ignore and (k not in c1 or c1[k] != c2[k]):
			return False
	return True


def load_decks():
	print("Loading all deck files...")
	decks = Path("decks")
	for path in decks.iterdir():
		if re.search(r'\.txt$', str(path)) is not None:
			import_deck(path)
	print("Loaded {} cards.".format(len(cards)))


def load_db():
	print("\nLoading DB informations...")
	load_table("aspects", "name, id", aspects)
	load_table("colors", "name, id", colors)
	load_table("rarities", "id, id", rarities)
	load_table("triggers", "name, id", triggers)
	load_table("types", "name, id", types)
	load_table("universes", "id, id", universes)
	load_table("extensions", "name, id", extensions)
	load_table("traits", "name, id", traits)
	print("Done.")


def check_cards():
	print("\nChecking cards properties...")

	def check_property(prop, availables):
		if not card[prop] in availables:
			print("\n/!\\ ERROR: Missing {} in database: {} /!\\".format(prop, card[prop]))
			print("CARD:")
			print_dict(card)
			print("DEFINED VALUES:")
			print_dict(availables)
			raise SilentException()

	for card in cards:
		check_property("side", aspects)
		check_property("color", colors)
		check_property("rarity", rarities)
		check_property("trigger", triggers)
		check_property("card_type", types)
	print("Done.")


def insert_exts():
	global extensions

	print("\nInserting Extensions...")
	print("-> {} extensions already in DB".format(len(extensions)))
	max_id = reduce(lambda x, item: x if x > item[1] else item[1], extensions.items(), 0)
	print("-> Max extension ID is: {}".format(max_id))
	exts = {}

	for card in cards:
		ext_name = card["deck"]
		ext_code = card["extension"]
		ext_uni = card["universe"]
		if ext_name in extensions:
			continue
		elif ext_name in exts:
			counts = exts[ext_name]["code"]
			if ext_code in counts:
				counts[ext_code] += 1
			else:
				counts[ext_code] = 1
		else:
			max_id += 1
			ext_no = max_id
			exts[ext_name] = {
				"no": ext_no,
				"name": ext_name,
				"code": {ext_code: 1},
				"uni": ext_uni
			}

	for ext_name in exts:
		ext = exts[ext_name]
		most_code = None
		most_count = 0
		most_conflict = False
		for code in ext["code"]:
			count = ext["code"][code]
			if count > most_count:
				most_count = count
				most_code = code
				most_conflict = False
			elif count == most_count:
				most_conflict = True
		if most_conflict:
			print("Unable to guess extension code")
			print(ext)
			raise SilentException()
		ext["code"] = most_code

	print("-> {} missing extensions".format(len(exts)))

	for k in exts:
		ext = exts[k]
		db.execute(text("INSERT INTO extensions SET id = :i, name = :n, abbreviation = :a, universe = :u"),
				i=ext["no"], n=ext["name"], a=ext["code"], u=ext["uni"])

	if len(exts) > 0:
		extensions = {}
		load_table("extensions", "name, id", extensions)

	print("Done.")


def insert_traits():
	global traits

	print("\nInserting Traits...")
	print("-> {} traits already in DB".format(len(traits)))
	missing_traits = {}

	def check_trait(trait):
		if not trait in traits and not trait in missing_traits:
			missing_traits[trait] = true

	for card in cards:
		check_trait(card["trait1"])
		check_trait(card["trait2"])

	print("-> {} missing traits".format(len(missing_traits)))

	for trait in missing_traits:
		db.execute(text("INSERT INTO traits SET name = :name"), name=trait)

	if len(missing_traits) > 0:
		traits = {}
		load_table("traits", "name, id", traits)

	print("Done.")


def insert_cards():
	print("\nInserting Cards...")

	insert_data = []
	keys = {}
	exts_ids = {}
	name_abbr = {}

	print("-> Loading extensions abbreviations...")
	for (abbr, id, name) in db.execute("SELECT abbreviation, id, name FROM extensions"):
		name_abbr[name] = abbr
		if abbr in exts_ids and not re.match(r'Pack$', name):
			pass
		else:
			exts_ids[abbr] = id

	fixed = 0
	print("-> Checking for re-edits...")
	for card in cards:
		if card["extension"] != name_abbr[card["deck"]]:
			card["real_extension"] = exts_ids[card["extension"]]
			fixed += 1
		else:
			card["real_extension"] = extensions[card["deck"]]
	print("-> Fixed {} re-edits...".format(fixed))

	print("-> Checking for duplicate keys...")
	for card in cards:
		key = card["key"] = "{}-{}-{}".format(card["real_extension"], card["id"], card["version"])
		if key in keys:
			if not card_equals(card, keys[key]):
				print("\n/!\\ ERROR: Duplicate key {} detected /!\\".format(key))
				print("CARD 1:")
				print_dict(card)
				print("CARD 2:")
				print_dict(keys[key])
				raise SilentException()
		else:
			keys[key] = card

	print("-> {} distinct cards available...".format(len(keys)))

	print("-> Constructing insert data...")
	for key in keys:
		card = keys[key]
		insert_data.append({
			"id": card["primary_id"],
			"version": card["version"],
			"extension": card["real_extension"], #exts_ids[card["extension"]],
			"name": card["name"],
			"ability": card["text"],
			"flavor": card["flavor"],
			"rarity": card["rarity"],
			"type": types[card["card_type"]],
			"color": colors[card["color"]],
			"trigger": triggers[card["trigger"]],
			"level": card["level"],
			"cost": card["cost"],
			"power": card["power"],
			"soul": card["soul"],
			"trait1": traits[card["trait1"]],
			"trait2": traits[card["trait2"]],
		})

	print("-> Running")
	db.execute(Cards.insert(), insert_data)
	print("Done.")


def main():
	load_decks()
	load_db()
	check_cards()
	insert_exts()
	insert_traits()
	insert_cards()


if __name__ == "__main__":
	try:
		main()
	except SilentException:
		pass
	except:
		raise
