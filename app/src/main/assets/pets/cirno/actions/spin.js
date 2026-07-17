action = {
	id: "spin",

	start(pet) {
		// 调用 spin 材质动画
		pet.animation.switchTo("spin");

		// 初始速度：随机一个方向，速度稍微快一点才有台球感
		const speedBase = 32;
		let angle = Math.random() * Math.PI * 2;
		this.imgW = pet.getImageView().width;
		this.imgH = pet.getImageView().height;
		this.vx = Math.cos(angle) * speedBase;
		this.vy = Math.sin(angle) * speedBase;

		this.duration = 600 + Math.random() * 600;
		this.timer = 0;
	},

	update(pet) {
		let currentX = pet.getX();
		let currentY = pet.getY();


		// 1. 预测下一帧的位置
		let nextX = currentX + this.vx;
		let nextY = currentY + this.vy;
		
		// 1.5. 模拟阻力
		this.vx -= this.vx / 2000
		this.vy -= this.vy / 2000
		
		// 2. 边界碰撞检测 (台球反弹逻辑)

		// 左右撞墙：速度 X 取反
		if (nextX < 0 || nextX > device.width - this.imgW) {
			this.vx *= -1;
			// 修正位置防止卡墙
			nextX = nextX < 0 ? 0 : device.width - this.imgW;
		}

		// 上下撞墙：速度 Y 取反
		if (nextY < 0 || nextY > device.height - this.imgH) {
			this.vy *= -1;
			// 修正位置防止卡墙
			nextY = nextY < 0 ? 0 : device.height - this.imgH;
		}

		// 3. 执行位移
		if (pet.dragging === false) {
			// 注意：这里直接计算出增量传给 move，或者用 setPosition
			pet.move(this.vx, this.vy);
		}

		// 4. 自转反馈 (可选：根据速度方向轻微改变镜像，或者保持 spin 动画原样)
		if (this.vx < 0) {
			pet.getImageView().scaleX = -1;
		} else {
			pet.getImageView().scaleX = 1;
		}

		// 5. 计时结束逻辑
		this.timer++;
		if (this.timer > this.duration) {
			pet.action.switchTo("idle");
		}
	}
};