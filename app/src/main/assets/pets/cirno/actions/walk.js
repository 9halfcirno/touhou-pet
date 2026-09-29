export default {
	start(pet) {
		pet.animation.switchTo("walk_front");
		this.angle = Math.random() * Math.PI * 2;
		this.speed = 4;

		// 【优化 1：启动时缓存所有静态/初始数据】
		// 避免在 update() 循环中重复跨语言读取
		this.imgW = pet.view.width;
		this.imgH = pet.view.height;
		
		// 将 Java 层的真实坐标拉取到 JS 内存中作为“影子坐标”
		this.curX = pet.x;
		this.curY = pet.y;

		this.changeDirTimer = 0;
		this.nextChangeTime = Math.random() * 60 + 30;
		this.totalTimer = 0;
	},

	update(pet) {
		// 【防抽搐逻辑】：被拖动时暂停运算，并重新同步 Java 层的新坐标
		if (pet.state.dragging) {
			this.curX = pet.x;
			this.curY = pet.y;
			return;
		}

		this.changeDirTimer++;
		if (this.changeDirTimer > this.nextChangeTime) {
			this.angle += (Math.random() - 0.5) * (Math.PI / 2);
			this.changeDirTimer = 0;
			this.nextChangeTime = Math.random() * 60 + 60;
		}

		let vx = Math.cos(this.angle) * this.speed;
		let vy = Math.sin(this.angle) * this.speed;
		let hitWall = false;

		// 【优化 2：纯 JS 内存计算】
		// 不再调用 pet.getX() 和 pet.getY()，直接累加影子坐标，耗时几乎为 0
		this.curX += vx;
		this.curY += vy;

		// 边界碰撞检测与强行修正
		if (this.curX < 0) {
			this.curX = 10;
			this.angle = 0; // 强制向右
			hitWall = true;
		} else if (this.curX > device.width - this.imgW) {
			this.curX = device.width - this.imgW - 10;
			this.angle = Math.PI; // 强制向左
			hitWall = true;
		}

		if (this.curY < 0) {
			this.curY = 10;
			this.angle = Math.PI / 2; // 强制向下
			hitWall = true;
		} else if (this.curY > device.height - this.imgH) {
			this.curY = device.height - this.imgH - 10;
			this.angle = -Math.PI / 2; // 强制向上
			hitWall = true;
		}

		if (hitWall) {
			this.changeDirTimer = -30; // 撞墙冷却
		}

		// 【优化 3：单向推送 UI 更新】
		// 算完之后，每帧只调一次 JNI，把结果塞给 Java
		pet.setPosition(Math.round(this.curX), Math.round(this.curY));

		// 【优化 4：状态差异化更新】
		// 只有在方向真的发生反转时，才去触发 JNI 修改 scaleX
		if (Math.abs(vx) > 0.5) {
			let targetScale = (vx < 0) ? -1 : 1;
			pet.view.scale.x = targetScale;
		}

		this.totalTimer++;
		if (this.totalTimer > 600) {
			pet.action.switchTo("idle");
		}
	},
	
	touch(pet) {
		pet.action.switchTo("fall")
	}
};