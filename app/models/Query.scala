package models

import controllers._
import controllers.mysql._
import slick.jdbc.GetResult

/**
  * Collection of queries used for page generation.
  * It is a last desperate attempt to produce clean code using only
  * the plain-SQL API from Slick. I miss you TableQuery !
  */
object Query {
	case class Universe(aspect: String, id: String, name: String)
	implicit val GetUniverse = GetResult(r => Universe(r.<<, r.<<, r.<<))

	case class Extension(id: Int, name: String, abbr: String)
	implicit val GetExtension = GetResult(r => Extension(r.<<, r.<<, r.<<))

	implicit val GetCompleteCard = GetResult(r => CompleteCard(r.<<, r.<<, r.<<, r.<<, (r.<<, r.<<), (r.<<, r.<<),
		r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, (r.<<, r.<<), (r.<<, r.<<), r.<<))

	case class DeckData(id: Int, name: String, cards: Seq[(CompleteCard, Int)]) {
		lazy val cardsByLevels = packByCategory(cards) { case (cc, qte) => cc }
		lazy val universes = cards.map { case (cc, qte) => (cc.universe_id, cc.universe) }.distinct

		def quantityOf(card: String, version: String) = {
			cards.find { case (c, _) =>
				c.id == card && c.version == version
			}.map { case (_, qte) =>
				qte
			}.getOrElse(0)
		}
	}

	def packByCategory[T](cards: Seq[T])(extractor: (T) => CompleteCard) = {
		cards.packWithKey { case t =>
			val card = extractor(t)
			card.tpe match {
				case "Climax" => "Climax"
				case _ => s"Level ${card.level}"
			}
		}
	}

	// ---------------------------------
	// Collection
	// ---------------------------------

	case class UniverseListRow(universe: Universe, extensions: Int, cards: Int)
	implicit val GetUniverseListRow = GetResult(r => UniverseListRow(r.<<[Universe], r.<<, r.<<))
	def universeList = sql"""
		SELECT u.aspect, u.id, u.name,
			(SELECT COUNT(*) FROM extensions AS e WHERE e.universe = u.id),
			(SELECT COUNT(*) FROM cards AS c JOIN extensions AS e ON e.id = c.extension WHERE e.universe = u.id)
		FROM universes AS u
		ORDER BY u.aspect ASC, u.name ASC
	""".as[UniverseListRow].run

	def universeName(id: String) = sql"""
		SELECT name FROM universes WHERE id = $id
	""".as[String].head.run

	def updateCollection(user: String, card: String, version: String, mod: Int) = sqlu"""
		INSERT INTO collections (user, card, version, quantity)
		VALUES ($user, $card, $version, GREATEST(0, $mod))
      ON DUPLICATE KEY UPDATE quantity = GREATEST(0, quantity + $mod)
	""".run

	def cardDetail(identifier: String, user: String) = {
		CompleteCards.filter(_.identifier === identifier).joinLeft(Collections).on { case (cc, col) =>
			cc.id === col.card && cc.version === col.version && col.user === user
		}.map { case (cc, col) =>
			(cc, col.map(_.quantity).ifNull(0))
		}.result.head.run
	}

	def cardCombos(card: String, user: String) = sql"""
		SELECT c.*, IFNULL(col.quantity, 0)
		FROM combos AS cb
		INNER JOIN complete_cards AS c ON c.id = cb.card
		LEFT JOIN collections AS col ON col.card = c.id AND col.version = c.version AND col.user = $user
		WHERE cb.id IN (SELECT id FROM combos WHERE card = $card)
		AND c.name != (SELECT NAME FROM cards WHERE id = $card LIMIT 1)
	""".as[(CompleteCard, Int)].run

	def decksContaining(card: String, version: String, user: String) = sql"""
		SELECT DISTINCT deck_id, deck_name, quantity
		FROM deck_cards
      WHERE id = $card AND version = $version AND deck_user = $user
	""".as[(Int, String, Int)].run

	// ---------------------------------
	// Decks
	// ---------------------------------

	case class DeckListRow(id: Int, name: String, universes: Seq[(String, String)], ncx_count: Int, cx_count: Int)
	def deckList(user: String) = {
		case class Deck(id: Int, name: String)
		case class Universe(id: Option[String], name: Option[String]) {
			require(id.isDefined == name.isDefined)
			def isDefined = id.isDefined
			def get = (id.get, name.get)
		}
		case class Count(ncx: Int, cx: Int)
		case class Row(deck: Deck, universe: Universe, count: Count)
		implicit val getRow = GetResult(r => Row(Deck(r.<<, r.<<), Universe(r.<<, r.<<), Count(r.<<, r.<<)))

		sql"""
			SELECT DISTINCT d.id, d.name, dc.universe, dc.universe_name,
				(SELECT IFNULL(SUM(quantity), 0) FROM deck_cards
				 WHERE deck_id = d.id AND universe = dc.universe AND type != 'CX'),
				(SELECT IFNULL(SUM(quantity), 0) FROM deck_cards
				 WHERE deck_id = d.id AND universe = dc.universe AND type = 'CX')
			FROM decks AS d
			LEFT JOIN deck_cards AS dc ON dc.deck_id = d.id
			WHERE d.user = $user
			ORDER BY d.name ASC, dc.universe_name ASC
		""".as[Row].run.map { case rows =>
			rows.pack(r => r.deck).map { data =>
				val deck = data.head.deck
				val universes = data.collect { case Row(_, universe, _) if universe.isDefined => universe.get }
				DeckListRow(deck.id, deck.name, universes, data.map(_.count.ncx).sum, data.map(_.count.cx).sum)
			}
		}
	}

	def createDeck(name: String, user: String) = sqlu"""
		INSERT INTO decks (name, user) VALUES ($name, $user)
	""".run

	def deleteDeck(id: Int, user: String) = sqlu"""
		DELETE FROM decks
		WHERE id = $id AND user = $user
		LIMIT 1
	""".run

	def deckName(id: Int) = sql"""
		SELECT name FROM decks WHERE id = $id
	""".as[String].head.run

	def deckOwner(id: Int) = sql"""
		SELECT user FROM decks WHERE id = $id
	""".as[String].head.run

	def renameDeck(id: Int, name: String, user: String) = sqlu"""
		UPDATE decks SET name = $name
		WHERE id = $id AND user = $user
	""".run

	def deckData(id: Int, user: String) = {
		for {
			name <- Decks.filter(d => d.id === id && d.user === user).map(_.name).result.head.run
			cards <- CompleteCards.join(DeckContents).on { case (cc, dc) =>
				cc.id === dc.card && cc.version === dc.version && dc.deck === id
			}.map { case (cc, dc) =>
				(cc, dc.quantity)
			}.sortBy { case (cc, q) =>
				(cc.level.desc, cc.tpe, cc.identifier)
			}.result.run
		} yield {
			DeckData(id, name, cards)
		}
	}

	def updateDeck(deck: Int, card: String, version: String, mod: Int) = sqlu"""
		INSERT INTO deck_contents (deck, card, version, quantity)
		VALUES ($deck, $card, $version, GREATEST(0, $mod))
      ON DUPLICATE KEY UPDATE quantity = GREATEST(0, quantity + $mod)
	""".run

	// ---------------------------------
	// Auth
	// ---------------------------------

	def login(login: String, pass: String) = sqlu"""
		UPDATE users SET lastConnection = NOW()
		WHERE login = LOWER($login) AND password = HASHPASS(login, $pass)
		LIMIT 1
	""".run.map(_ == 1)

	def register(login: String, mail: String, pass: String) = sqlu"""
		INSERT INTO users
		SET login = LOWER($login), email = $mail, password = HASHPASS($login, $pass)
	""".run

	def user(login: String) = sql"""
		SELECT login, email, disable_tips FROM users WHERE active = 1 AND login = $login
	""".as[(String, String, Boolean)].headOption.run
}
