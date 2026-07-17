export default {
	id: "circles",

	start(pet) {
		pet.animation.switchTo("circles").onEnd(() => {
			pet.action.switchTo("idle")
		});
	}, // 没必要有update
	update(pet) {
	}
}