package exhibition.event.impl;

import exhibition.event.Event;

public class EventMouse extends Event {
	private int buttonID;
	private boolean mouseDown;

	public void fire(int buttonID, boolean mouseDown) {
		this.buttonID = buttonID;
		this.mouseDown = mouseDown;
		super.fire();
	}

	public int getButtonID() {
		return buttonID;
	}

	public void setButtonID(int buttonID) {
		this.buttonID = buttonID;
	}

	public boolean isMouseDown() {
		return mouseDown;
	}

	public boolean isMotionEvent() {
		return buttonID == -1;
	}

	public void setMouseDown(boolean mouseDown) {
		this.mouseDown = mouseDown;
	}
}
