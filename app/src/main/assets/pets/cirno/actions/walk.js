action = {
	id: "walk",

	start(pet) {
		pet.animation.switchTo("walk_front");
		this.speed = 4;

		this.totalTimer = 0;
	},

	update(pet) {
		if (pet.state.dragging) {
			return;
		}




		this.totalTimer++;
		if (this.totalTimer > 600) {
			pet.action.switchTo("idle");
		}
	}
};