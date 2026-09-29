const actions = [
	"walk",
	"circles"
]

export default {
	id: "idle",

	start(pet) {
		pet.animation.switchTo("idle");
		this.startTime = Date.now();
		this.during = Math.floor(Math.random() * 8000 + 2000)
	},

	update(pet) {
		if (Date.now() - this.startTime > this.during) {
//			pet.action.switchTo("circles")
			pet.dialog.hidden();
			pet.action.switchTo(actions[Math.floor(Math.random() * actions.length)])
		}
	}
}