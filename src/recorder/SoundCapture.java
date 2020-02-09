//https://www.dreamincode.net/forums/topic/339799-swing-to-javafx/
package recorder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.paint.Color;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;




//workaround for a problem caused by main class
//extending Application.  Use NewMain for main class
class NewMain{
	public static void main(String[] args) {
		SoundCapture.main(args);;
	}
}

public class SoundCapture extends Application {

	String sl = "";
	int sliderVal = 0;
	int scaledVal = 0;
	int scratchPad;

	private final Label label = new Label();
	private Button playB, captB;
	private Slider slider;

	final int bufSize = 16384;
	Capture capture = new Capture();
	Playback playback = new Playback();
	AudioInputStream audioInputStream;
	String errStr;
	double duration, seconds;

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage stage) throws Exception {
		GridPane grid = new GridPane();
		grid.setMinSize(400, 200);
		grid.setVgap(10);
		grid.setAlignment(Pos.CENTER);

		grid.getColumnConstraints().add(new ColumnConstraints(50));
		grid.getColumnConstraints().add(new ColumnConstraints(75));
		grid.getColumnConstraints().add(new ColumnConstraints(75));
		grid.getColumnConstraints().add(new ColumnConstraints(75));
		grid.getColumnConstraints().add(new ColumnConstraints(50));

		playB = new Button("Play");
		captB = new Button("Record");
		slider = new Slider(0, 200, 200);
		slider.setMajorTickUnit(25);
		slider.setMinorTickCount(5);
		slider.setShowTickMarks(true);
		slider.setShowTickLabels(true);

		GridPane.setHalignment(label, javafx.geometry.HPos.CENTER);

		playB.setPrefWidth(75.0);
		captB.setPrefWidth(75.0);

		grid.add(playB, 1, 0);
		grid.add(captB, 3, 0);
		grid.add(label, 1, 2, 3, 1);
		grid.add(slider, 0, 3, 5, 1);

		playB.setOnAction((event) -> {
			if (playB.getText().startsWith("Play")) {
				playback.start();
				Platform.runLater(() -> captB.setDisable(true));
				Platform.runLater(() -> playB.setText("Stop"));
			} else {
				playback.stop();
				captB.setDisable(false);
				playB.setText("Play");
				System.err.println("stopped");
			}
		});

		captB.setOnAction((event) -> {
			if (captB.getText().startsWith("Record")) {
				capture.start();
				Platform.runLater(() -> playB.setDisable(true));
				Platform.runLater(() -> captB.setText("Stop"));
			} else {
				capture.stop();
				playB.setDisable(false);
				label.setText("");
				System.err.println("stopped");
				captB.setText("Record");
			}
		});

		slider.valueProperty().addListener((ObservableValue) -> {
			getScaledSliderVal();
		});

		// scaledVal value will initially be zero so fix that
		getScaledSliderVal();

		// grid.setGridLinesVisible(true);
		Scene scene = new Scene(grid);
		stage.setTitle("Capture/Playback");
		stage.setScene(scene);

		stage.show();

	}

	private void getScaledSliderVal() {
		sliderVal = (int) slider.getValue();
		// scale 16 bits to 0 - 200 slider
		scratchPad = sliderVal * 65535;
		scratchPad /= 200;
		scaledVal = scratchPad;
	}

	public void close() {
		if (playback.thread != null) {
			// playB.doClick(0);
		}
		if (capture.thread != null) {
			// captB.doClick(0);
		}
	}

	/**
	 * Write data to the OutputChannel.
	 */
	public class Playback implements Runnable {

		SourceDataLine line;

		Thread thread;

		public void start() {
			errStr = null;
			thread = new Thread(this);
			thread.setName("Playback");
			thread.start();
		}

		public void stop() {
			thread = null;
		}

		private void shutDown(String message) {
			if ((errStr = message) != null) {
				System.err.println(errStr);

			}
			if (thread != null) {
				thread = null;
				Platform.runLater(() -> captB.setDisable(false));
				Platform.runLater(() -> playB.setText("Play"));
			}
		}

		public void run() {

			// make sure we have something to play
			if (audioInputStream == null) {
				shutDown("No loaded audio to play back");
				return;
			}
			// reset to the beginnning of the stream
			try {
				audioInputStream.reset();
			} catch (Exception e) {
				shutDown("Unable to reset the stream\n" + e);
				return;
			}

			// get an AudioInputStream of the desired format for playback
			AudioFormat.Encoding encoding = AudioFormat.Encoding.PCM_SIGNED;
			float rate = 44100.0f;
			int channels = 2;
			int frameSize = 4;
			int sampleSize = 16;
			boolean bigEndian = true;

			AudioFormat format = new AudioFormat(encoding, rate, sampleSize, channels, (sampleSize / 8) * channels,
					rate, bigEndian);

			AudioInputStream playbackInputStream = AudioSystem.getAudioInputStream(format, audioInputStream);

			if (playbackInputStream == null) {
				shutDown("Unable to convert stream of format " + audioInputStream + " to format " + format);
				return;
			}

			// define the required attributes for our line,
			// and make sure a compatible line is supported.
			DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
			if (!AudioSystem.isLineSupported(info)) {
				shutDown("Line matching " + info + " not supported.");
				return;
			}

			// get and open the source data line for playback.
			try {
				line = (SourceDataLine) AudioSystem.getLine(info);
				line.open(format, bufSize);
			} catch (LineUnavailableException ex) {
				shutDown("Unable to open the line: " + ex);
				return;
			}

			// play back the captured audio data
			int frameSizeInBytes = format.getFrameSize();
			int bufferLengthInFrames = line.getBufferSize() / 8;
			int bufferLengthInBytes = bufferLengthInFrames * frameSizeInBytes;
			byte[] data = new byte[bufferLengthInBytes];
			int numBytesRead = 0;

			// start the source data line
			line.start();

			System.err.println("playing back");

			while (thread != null) {
				try {
					if ((numBytesRead = playbackInputStream.read(data)) == -1) {
						break;
					}
					int numBytesRemaining = numBytesRead;
					while (numBytesRemaining > 0) {
						numBytesRemaining -= line.write(data, 0, numBytesRemaining);
					}
				} catch (Exception e) {
					shutDown("Error during playback: " + e);
					break;
				}
			}
			// we reached the end of the stream.
			// let the data play out, then
			// stop and close the line.
			if (thread != null) {
				line.drain();
			}
			line.stop();
			line.close();
			line = null;
			shutDown(null);
		}
	} // End class Playback

	/**
	 * Reads data from the input channel and writes to the output stream
	 */
	class Capture implements Runnable {

		TargetDataLine line;

		Thread thread;

		public void start() {
			errStr = null;
			thread = new Thread(this);
			thread.setName("Capture");
			thread.start();
		}

		public void stop() {
			thread = null;
		}

		private void shutDown(String message) {
			if ((errStr = message) != null && thread != null) {
				thread = null;
				Platform.runLater(() -> playB.setDisable(false));
				Platform.runLater(() -> captB.setText("Record"));
				System.err.println(errStr);
			}
		}

		public void run() {

			duration = 0;
			audioInputStream = null;

			// define the required attributes for our line,
			// and make sure a compatible line is supported.
			AudioFormat.Encoding encoding = AudioFormat.Encoding.PCM_SIGNED;
			float rate = 44100.0f;
			int channels = 2;
			int frameSize = 4;
			int sampleSize = 16;
			boolean bigEndian = true;

			AudioFormat format = new AudioFormat(encoding, rate, sampleSize, channels, (sampleSize / 8) * channels,
					rate, bigEndian);

			DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

			if (!AudioSystem.isLineSupported(info)) {
				shutDown("Line matching " + info + " not supported.");
				return;
			}

			// get and open the target data line for capture.
			try {
				line = (TargetDataLine) AudioSystem.getLine(info);
				line.open(format, line.getBufferSize());
			} catch (LineUnavailableException ex) {
				shutDown("Unable to open the line: " + ex);
				return;
			} catch (SecurityException ex) {
				shutDown(ex.toString());
				// JavaSound.showInfoDialog();
				return;
			} catch (Exception ex) {
				shutDown(ex.toString());
				return;
			}

			// play back the captured audio data
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			int frameSizeInBytes = format.getFrameSize();
			int bufferLengthInFrames = line.getBufferSize() / 8;
			int bufferLengthInBytes = bufferLengthInFrames * frameSizeInBytes;
			byte[] data = new byte[bufferLengthInBytes];
			int numBytesRead;
			long left;
			long right;

			line.start();
///////////////////////////////////////////////////////////////////////////////////////////////////////////////
// look for either channel to be above scaled value from slider

			System.err.println("waiting for trigger");
			Platform.runLater(() -> label.setTextFill(Color.web("black")));
			Platform.runLater(() -> label.setText("Waiting for Trigger"));
			while (thread != null) {

				// read one frame and test for sound in either channel above slider threshold
				if ((numBytesRead = line.read(data, 0, frameSize)) == -1) {
					break;
				}

				left = data[0];
				left = left << 8;
				left |= (data[1] & 0xffL); // don't sign extend lower byte!

				right = data[2];
				right = right << 8;
				right |= (data[3] & 0xffL); // don't sign extend lower byte!

				if ((Math.abs(right) > scaledVal) || (Math.abs(left) > scaledVal)) {
					Platform.runLater(() -> label.setTextFill(Color.web("red")));
					Platform.runLater(() -> label.setText("Recording Triggered"));
					break;
				}
			}

///////////////////////////////////////////////////////////////////////////////////////////////////////////////
			if (thread != null) {
				System.err.println("recording triggered");
			}

			// triggered, so start regular recording here
			while (thread != null) {
				if ((numBytesRead = line.read(data, 0, bufferLengthInBytes)) == -1) {
					break;
				}
				out.write(data, 0, numBytesRead);
			}

			// we reached the end of the stream.
			// stop and close the line.
			line.stop();
			line.close();
			line = null;

			// stop and close the output stream
			try {
				out.flush();
				out.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}

			// load bytes into the audio input stream for playback
			byte audioBytes[] = out.toByteArray();
			ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
			audioInputStream = new AudioInputStream(bais, format, audioBytes.length / frameSizeInBytes);

			long milliseconds = (long) ((audioInputStream.getFrameLength() * 1000) / format.getFrameRate());
			duration = milliseconds / 1000.0;

			try {
				audioInputStream.reset();
			} catch (Exception ex) {
				ex.printStackTrace();
				return;
			}

		}
	} // End class Capture
}
