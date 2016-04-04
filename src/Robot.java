
import java.lang.Math;

import lejos.hardware.Button;
import lejos.hardware.motor.EV3LargeRegulatedMotor;
import lejos.hardware.port.MotorPort;
import lejos.hardware.port.SensorPort;
import lejos.hardware.sensor.EV3UltrasonicSensor;
import lejos.robotics.SampleProvider;
import lejos.utility.Delay;



public class Robot{
	private static boolean foundObject = false;
	//public static TrackerReader tracker = new TrackerReader();
	EV3LargeRegulatedMotor pan;
	EV3LargeRegulatedMotor tilt1;
	EV3LargeRegulatedMotor tilt2;
	EV3LargeRegulatedMotor grabber;
	EV3UltrasonicSensor dSensor;
	SampleProvider sp;
	float sample[] = {0};
	int[] tracker = {0,0,0,0};
	double distanceThres = 0.04;
	
	private final double h = 120;
	private final double s = 180;
	
	double psi;
	double phi;
	double gammaForward;
	double ettaForward;
	
	boolean ESC;
	
	//private final double k1;
	private final double k2 = 0.9;
	
	double radiansPerTick;
	
	public Robot() {

		//RemoteEV3 brick = new RemoteEV3("10.0.1.1");
		pan = new EV3LargeRegulatedMotor(MotorPort.B);
		tilt1 = new EV3LargeRegulatedMotor(MotorPort.C);
		tilt2 = new EV3LargeRegulatedMotor(MotorPort.D);
		grabber = new EV3LargeRegulatedMotor(MotorPort.A);
		pan.setSpeed(50);
		tilt1.setSpeed(50);
		tilt2.setSpeed(50);
		dSensor = new EV3UltrasonicSensor(SensorPort.S1);
		sp = dSensor.getDistanceMode();
		sample = new float[1];
		
		ESC = false;
		
		double distancePerTick = (Math.PI*0.056)/360;
		double ticksPerRotation = (2*Math.PI*0.05875)/ distancePerTick;
		radiansPerTick = (2*Math.PI)/ ticksPerRotation;
		
	}
	
	public void setInitialAngles() {
		tiltTo(-35);

		psi = (tilt1.getTachoCount() + tilt2.getTachoCount()) / 2.0;
		phi = pan.getTachoCount();
	}
	
	public void approxFeedForwardTerms() {
		try {
			int panAngle2 = 5;
			double errorx = trackerTargetX() - trackerX();
			double gamma1 = Math.toRadians(phi) + Math.atan(errorx/2.79);
			pan.rotateTo((int) phi + panAngle2);
			errorx = trackerTargetX() - trackerX();
			double gamma2 = Math.toRadians(phi + panAngle2) + Math.atan(errorx/2.79);
			pan.rotateTo((int) phi);
			gammaForward = gamma2 - gamma1;
			
			double errory = trackerTargetY() - trackerY();
			double tiltAngle2 = psi + 5;
			double etta1 = Math.toRadians(psi) + Math.atan(errory/2.79);
			tiltTo((int) tiltAngle2);
			errory = trackerTargetY() - trackerY();
			double etta2 = Math.toRadians(tiltAngle2) + Math.atan(errory/2.79);
			tiltTo((int) psi);
			ettaForward = etta2 - etta1;
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			this.closePorts();
		}
		
	}
	
	public double calcVelocity() {
		System.out.println("phi is: " + phi);
		System.out.println("psi is: " + psi);
		double b = h / Math.tan(Math.toRadians(psi));
		double normESquared = Math.pow(b, 2) + Math.pow(s, 2) - 2*b*s*Math.cos(Math.toRadians(180 - phi));
		double alpha = Math.asin((b*Math.sin(Math.toRadians(180 - phi)))/Math.sqrt(normESquared));
		//double velocity = Math.sqrt(normESquared)*Math.cos(alpha)*k1;
		double angularVelocity = k2*alpha;
		System.out.println("Angular velocity is: " + angularVelocity);
		return angularVelocity;
	}
	
	private double calcTachoCount(double angularVelocity) {
		double tachoCount = (2*angularVelocity) / radiansPerTick;
		System.out.println("Tacho Count is: " + tachoCount);
		return tachoCount;
		
	}
	
	public void closePorts(){
		//tilt1.close();
		//tilt2.close();
		//pan.close();
		//dSensor.close();
		//grabber.close();
	}
	
	public int encode(double count) {
		int tachoCount = (int) Math.round(count);
		int motor = (tachoCount >= 0) ? 0 : 1;
		System.out.println((int) ((Math.abs(tachoCount)*10 + motor)*Math.signum(tachoCount)));
		return (int) ((Math.abs(tachoCount)*10 + motor)*Math.signum(tachoCount));
	}
	
	public void decode(int received) {
		int motor = received % 10;
		int tachoCount = (int) ((received - motor*Math.signum(motor)) / 10);
		System.out.println(tachoCount);
	}
	
	
	public void zeroy() {
		double MAX_ANGLE = 3;
		double errory;
		double kpsi = 0.01;
		//int tiltAngle2 = 5;

		//double psi = tilt1.getTachoCount();
		//double tiltAngle2 = psi + 5;
		//double etta1 = Math.toRadians(psi) + Math.atan(errory/2.79);
		//tiltTo((int) tiltAngle2);
		//errory = tracker.targety - tracker.y;
		//double etta2 = Math.toRadians(tiltAngle2) + Math.atan(errory/2.79);
		//tiltTo((int) psi);
		//double ettaForward = etta2 - etta1;
		tracker = BluetoothClient.read();
		errory = trackerTargetY() - trackerY();
		while (Math.abs(errory) > 5 && !this.ESC &&checkForObject()) {
			sp.fetchSample(sample,0);
			if(sample[0] <= distanceThres){
				break;
			}
			double psiChange = -1*(ettaForward - kpsi*errory) % 360;
			if (Math.abs(psiChange) > MAX_ANGLE) {
				psiChange = MAX_ANGLE * Math.signum(psiChange);
			}
			//System.out.println(psiChange);
			psi += psiChange;
			tiltTo((int) psi);
			//Delay.msDelay(2000);
			errory = trackerTargetY() - trackerY();
			//Delay.msDelay(500); 
		}
	}
	

	public void zerox() {
		double MAX_ANGLE = 3;
		double kphi = 0.1;
		//double phi = pan.getTachoCount();
		//double gamma1 = Math.toRadians(phi) + Math.atan(errorx/2.79);
		//pan.rotateTo(panAngle2);
		//errorx = tracker.targetx - tracker.x;
		//double gamma2 = Math.toRadians(panAngle2) + Math.atan(errorx/2.79);
		//pan.rotateTo((int) phi);
		//double gammaForward = gamma2 - gamma1;
		tracker = BluetoothClient.read();
		double errorx = trackerTargetX() - trackerX();
		while (Math.abs(errorx) > 5 && !this.ESC && checkForObject()) {
			sp.fetchSample(sample,0);
			if(sample[0] <= distanceThres){
				break;
			}
			//phi += (gammaForward - kphi*errorx) % 360;
			double phiChange = -1*(gammaForward - kphi*errorx) % 360;
			if (Math.abs(phiChange) > MAX_ANGLE) {
				phiChange = MAX_ANGLE * Math.signum(phiChange);
			}
			//System.out.println(phiChange);
			phi += phiChange;
			pan.rotateTo((int) phi);
			Delay.msDelay(100);
			errorx = trackerTargetX() - trackerX();
			//Delay.msDelay(2000);
		}
		
	}
	
	private void tiltTo(int i) {
		tilt1.rotateTo(i, true);//, true);
		tilt2.rotateTo(i);
	}
	
	public void grab() {
		grabber.rotate(-2200);
	}
	
	public void release() {
		grabber.rotate(2200);
	}
	
	public int trackerTargetX(){
		
		return tracker[2];
	}
	
	public int trackerTargetY(){
		
		return tracker[3];
	}
	
	public int trackerX(){
		
		return tracker[0];
	}
	
	public int trackerY(){
		return tracker[1];
	}
	
	public boolean checkForObject(){
		tracker = BluetoothClient.read();
		if(tracker[0] == -1 || tracker[1] == -1 || tracker[2] == -1 || tracker[3] == -1){
			return false;
		}else{
			return true;
		}
			
	}
	
	public static void main(String[] args) {
		//tracker.start();
		
		BluetoothServer.connect();
		BluetoothClient.connect();
		System.out.println("Done connecting to Tablet");
		Robot p = new Robot();
		p.setInitialAngles();
		System.out.println("Set inital angles, waitin for button press");
		Button.waitForAnyPress();
		while(!p.checkForObject()){}
		p.approxFeedForwardTerms();
		double errorx = p.trackerTargetX() - p.trackerX();
		double errory = p.trackerTargetY() - p.trackerY();
		double normTrackerError;
		double angularV;
		double tachoCount;
		
		while (!p.ESC) {
			p.sp.fetchSample(p.sample,0);
			System.out.println("Sensor distance measure " + p.sample[0]);
			if(p.sample[0] <= p.distanceThres){
				break;
			}
			if(!p.checkForObject()){
				System.out.println("Could not find object");
				continue;
			}
			errorx = p.trackerTargetX() - p.trackerX();
			errory = p.trackerTargetY() - p.trackerY();
	
			normTrackerError = Math.sqrt(Math.pow(errorx, 2) + Math.pow(errory, 2));
			if (normTrackerError > 80) {
				BluetoothServer.send(Integer.MAX_VALUE);
				p.zerox();
				p.zeroy();
				angularV = p.calcVelocity();
				tachoCount = p.calcTachoCount(angularV);
				BluetoothServer.send((int) Math.round(tachoCount));
			} else {
				BluetoothServer.send(Integer.MIN_VALUE);
			}
		}
		BluetoothServer.send(Integer.MAX_VALUE);
		p.grab();
		Button.waitForAnyPress();
		p.release();
		p.closePorts();
		BluetoothServer.disconnect();
		BluetoothClient.disconnect();
	}



}
