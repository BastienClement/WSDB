//
// Card collection update
//

var update_buffer = {};

function update_card(dataset, mod) {
	var id = dataset.id;

	if (id in update_buffer) {
		update_buffer[id] += mod;
		return;
	}

	update_buffer[id] = 0;
	setTimeout(function() {
		$.post(routes.controllers.Collection.update().url, {
			card: dataset.card,
			version: dataset.version,
			mod: mod
		}, function() {
			var buffer_mod = update_buffer[id];
			delete update_buffer[id];
			if (buffer_mod != 0) {
				update_card(dataset, buffer_mod);
			}
		});
	}, 250);
}

//
// Deck cards update
//

function update_deck(card, version, mod, cb, deck) {
	$.post(routes.controllers.Decks.update().url, {
		card: card,
		version: version,
		mod: mod,
		deck: deck
	}, function(data) {
		if (data.success) {
			if (cb) cb(data.success);
		} else {
			$("#modal-error").text(data.error);
			$("#modal").modal();
		}
	}, "json");
}
