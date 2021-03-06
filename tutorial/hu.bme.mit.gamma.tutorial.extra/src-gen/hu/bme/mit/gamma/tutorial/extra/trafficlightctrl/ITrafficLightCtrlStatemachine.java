package hu.bme.mit.gamma.tutorial.extra.trafficlightctrl;

import java.util.List;
import hu.bme.mit.gamma.tutorial.extra.IStatemachine;
import hu.bme.mit.gamma.tutorial.extra.ITimerCallback;

public interface ITrafficLightCtrlStatemachine extends ITimerCallback,IStatemachine {

	public interface SCILightCommands {
	
		public boolean isRaisedDisplayRed();
		
		public boolean isRaisedDisplayGreen();
		
		public boolean isRaisedDisplayYellow();
		
		public boolean isRaisedDisplayNone();
		
	public List<SCILightCommandsListener> getListeners();
	}
	
	public interface SCILightCommandsListener {
	
		public void onDisplayRedRaised();
		public void onDisplayGreenRaised();
		public void onDisplayYellowRaised();
		public void onDisplayNoneRaised();
		}
	
	public SCILightCommands getSCILightCommands();
	
	public interface SCIPoliceInterrupt {
	
		public void raisePolice();
		
	}
	
	public SCIPoliceInterrupt getSCIPoliceInterrupt();
	
	public interface SCIControl {
	
		public void raiseToggle();
		
	}
	
	public SCIControl getSCIControl();
	
}
