package hu.bme.mit.gamma.tutorial.finish.trafficlightctrl;

import hu.bme.mit.gamma.tutorial.finish.IStatemachine;
import hu.bme.mit.gamma.tutorial.finish.ITimerCallback;
import java.util.List;

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
