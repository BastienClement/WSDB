package views

object Utils {
	def cardImageURL(identifier: String) = {
		val id = identifier.replace('/', '_').replace('-', '_').toLowerCase
		s"http://loki.cpfk.net/card/$id.gif"
	}
}
