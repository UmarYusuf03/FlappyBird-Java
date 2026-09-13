import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;
import javax.swing.*;
import javax.sound.sampled.*;


public class FlappyBird extends JPanel implements ActionListener, KeyListener, MouseListener{
    int boardWidth = 360;
    int boardHeight = 640;

    //Images

    Image backgroundImg;
    Image birdImg;
    Image topPipeImg;
    Image bottomPipeImg;

    //Bird
    int birdX = boardWidth/8;
    int birdY = boardHeight/2;
    int birdWidth = 34;
    int birdHeight = 24;

    class Bird{
        int x = birdX;
        int y = birdY;
        int width = birdWidth;
        int height = birdHeight;
        Image img;

        Bird(Image img){
            this.img = img;
        }

    }

    //Pipes
    int pipeX = boardWidth;
    int pipeY = 0;
    int pipeWidth = 64;
    int pipeHeight = 512;

    class Pipe{
        int x = pipeX;
        int y = pipeY;
        int width = pipeWidth;
        int height = pipeHeight;
        Image img;
        boolean passed = false;
        // Tracks whether the point sound has already fired for this pipe,
        // so it only plays once as the bird reaches the mid-point of the gap
        boolean soundPlayed = false;

        Pipe(Image img){
            this.img = img;
        }
    }

    //game logic
    Bird bird;
    int velocityX = -4; //Move pipes to the left speed (This simulates birds moving to the right)
    int velocityY = 0; //move bird up/dowwn
    int gravity = 1;


    ArrayList<Pipe> pipes;
    Random random = new Random();

    Timer gameLoop;
    Timer placePipesTimer;

    boolean gameOver = false;
    // Prevents the game-over sound from firing on every repaint frame
    boolean gameOverSoundPlayed = false;
    // Whether all sounds are currently muted
    boolean soundMuted = false;
    // Stores the mute button's pixel bounds so mouseClicked can detect hits
    private java.awt.Rectangle muteButtonBounds = new java.awt.Rectangle();

    double score = 0;
    // Tracks the best score across all rounds in this session
    double highScore = 0;

    FlappyBird(){
        setPreferredSize(new Dimension(boardWidth, boardHeight));
        //setBackground(Color.blue);
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this); // needed to detect mute button clicks

        //load Images
        backgroundImg = new ImageIcon(getClass().getResource("./flappybirdbg.png")).getImage();
        birdImg = new ImageIcon(getClass().getResource("./flappybird.png")).getImage();
        topPipeImg = new ImageIcon(getClass().getResource("./toppipe.png")).getImage();
        bottomPipeImg = new ImageIcon(getClass().getResource("./bottompipe.png")).getImage();

        bird = new Bird(birdImg);

        pipes = new ArrayList<Pipe>();

        //placce pipes Timer
        placePipesTimer = new Timer(1500, new ActionListener(){
            @Override 
            public void actionPerformed(ActionEvent e){
                placePipes();
            }
        });

        placePipesTimer.start();

        //game timer
        gameLoop = new Timer(1000/60, this);
        gameLoop.start();
    }


    public void placePipes(){

        int randomPipeY = (int)(pipeY - pipeHeight/4 - Math.random()*(pipeHeight/2));
        int openingSpace = boardHeight/6 + (int)(Math.random()*(pipeHeight/3));

        Pipe topPipe = new Pipe(topPipeImg);
        topPipe.y = randomPipeY;
        pipes.add(topPipe);

        Pipe bottomPipe = new Pipe(bottomPipeImg);
        bottomPipe.y = topPipe.y + pipeHeight + openingSpace;

        pipes.add(bottomPipe);
    }

    // Stores the loaded pixel font so it's only read from disk once.
    // Using a cached field avoids re-loading the TTF file on every frame.
    private Font pixelFont;

    // Returns the pixel font at the requested size.
    // The first call loads "PressStart2P.ttf" from the same folder as the class files.
    // If loading fails for any reason, it falls back to the built-in Monospaced font.
    private Font getPixelFont(float size) {
        if (pixelFont == null) {
            try {
                // Read the TTF file bundled with the project resources
                java.io.InputStream is = getClass().getResourceAsStream("./PressStart2P.ttf");
                // Parse the raw bytes into a Java Font object
                Font base = Font.createFont(Font.TRUETYPE_FONT, is);
                // Register it with the system so FontMetrics can measure it correctly
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(base);
                pixelFont = base;
            } catch (Exception ex) {
                // Fallback: use a monospaced system font if the TTF can't be loaded
                pixelFont = new Font("Monospaced", Font.BOLD, 12);
            }
        }
        // deriveFont creates a new Font instance at the given point size
        // without reloading the file, so each call is cheap
        return pixelFont.deriveFont(size);
    }

    // --------------- SOUND HELPERS ---------------

    // Plays a short ascending "ding" when the bird passes a pipe.
    // The sound is generated purely in code (no audio files needed) and
    // runs on a background thread so it never pauses the game loop.
    private void playPointSound() {
        new Thread(() -> {
            try {
                int sampleRate = 44100;
                AudioFormat fmt = new AudioFormat(sampleRate, 16, 1, true, false);

                // Two quick rising tones: 880 Hz then 1320 Hz, each 60 ms
                // This gives a classic retro "coin" feel
                int[] freqs    = {880, 1320};
                int[] durations = {60,  60};
                byte[] buf = buildTones(freqs, durations, sampleRate);

                DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(fmt, buf.length);
                line.start();
                line.write(buf, 0, buf.length);
                line.drain();
                line.close();
            } catch (Exception ex) { /* sound unavailable – fail silently */ }
        }).start();
    }

    // Plays a descending "whomp" when the game ends.
    // Frequency slides from 400 Hz down to 80 Hz over ~500 ms,
    // giving a classic arcade "fail" effect.
    private void playGameOverSound() {
        new Thread(() -> {
            try {
                int sampleRate = 44100;
                AudioFormat fmt = new AudioFormat(sampleRate, 16, 1, true, false);

                int numSamples = (int)(sampleRate * 0.5); // 500 ms total
                byte[] buf = new byte[numSamples * 2];    // 16-bit = 2 bytes per sample

                double startFreq = 400.0;
                double endFreq   =  80.0;
                double phase = 0;

                for (int i = 0; i < numSamples; i++) {
                    // Linearly interpolate frequency from start to end
                    double t = (double) i / numSamples;
                    double freq = startFreq + (endFreq - startFreq) * t;

                    // Advance the phase by the current frequency step
                    phase += 2.0 * Math.PI * freq / sampleRate;

                    // Fade out volume near the end so it doesn't click
                    double envelope = Math.max(0.0, 1.0 - t * 0.8);
                    short sample = (short)(Math.sin(phase) * 32767 * 0.4 * envelope);

                    // Store as little-endian 16-bit
                    buf[i * 2]     = (byte)(sample & 0xff);
                    buf[i * 2 + 1] = (byte)((sample >> 8) & 0xff);
                }

                DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(fmt, buf.length);
                line.start();
                line.write(buf, 0, buf.length);
                line.drain();
                line.close();
            } catch (Exception ex) { /* sound unavailable – fail silently */ }
        }).start();
    }

    // Helper: builds a byte buffer containing multiple sine-wave tones played back-to-back.
    // freqs[] and durations[] must be the same length.
    // Each tone fades out over the last 10 ms to avoid audible clicks between tones.
    private byte[] buildTones(int[] freqs, int[] durationsMs, int sampleRate) {
        // Calculate total buffer size first
        int total = 0;
        for (int d : durationsMs) total += sampleRate * d / 1000;
        byte[] buf = new byte[total * 2];
        int pos = 0;

        for (int t = 0; t < freqs.length; t++) {
            int numSamples = sampleRate * durationsMs[t] / 1000;
            int fadeSamples = sampleRate * 10 / 1000; // 10 ms fade-out
            for (int i = 0; i < numSamples; i++) {
                double angle = 2.0 * Math.PI * freqs[t] * i / sampleRate;
                double env = (i > numSamples - fadeSamples)
                             ? (double)(numSamples - i) / fadeSamples
                             : 1.0;
                short sample = (short)(Math.sin(angle) * 32767 * 0.35 * env);
                buf[pos++] = (byte)(sample & 0xff);
                buf[pos++] = (byte)((sample >> 8) & 0xff);
            }
        }
        return buf;
    }

    // --------------- END SOUND HELPERS ---------------

    public void paintComponent(Graphics g){
        super.paintComponent(g);
        draw(g);
    }

    public void draw(Graphics g){
        //bg
        g.drawImage(backgroundImg, 0, 0, boardWidth, boardHeight, null);

        //bird
        g.drawImage(birdImg, bird.x, bird.y, bird.width, bird.height, null);

        for(int i=0; i<pipes.size(); i++){
            Pipe pipe = pipes.get(i);
            g.drawImage(pipe.img, pipe.x, pipe.y, pipe.width, pipe.height, null);
        }

        // Cast to Graphics2D so we can use advanced features like GradientPaint
        Graphics2D g2d = (Graphics2D) g;
        // Turn off text anti-aliasing to keep the pixel font looking sharp and blocky,
        // rather than smooth/blurred the way normal fonts are rendered
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        if (gameOver) {
            // --- GAME OVER SCREEN ---

            // Draw a dark semi-transparent rectangle over the whole screen
            // Alpha value 160 (out of 255) lets the game scene show through faintly
            g2d.setColor(new Color(0, 0, 0, 160));
            g2d.fillRect(0, 0, boardWidth, boardHeight);

            // Load the pixel font at 36pt for the big "GAME OVER" heading
            Font goFont = getPixelFont(36f);
            g2d.setFont(goFont);
            // FontMetrics lets us measure how wide/tall the text will be,
            // so we can calculate exact centered positions
            FontMetrics goFm = g2d.getFontMetrics(goFont);

            String line1 = "GAME";
            String line2 = "OVER";

            // Center each word horizontally:
            // X = (board width - text width) / 2
            int line1X = (boardWidth - goFm.stringWidth(line1)) / 2;
            int line2X = (boardWidth - goFm.stringWidth(line2)) / 2;

            // Place "GAME" just above the vertical center of the screen,
            // then "OVER" one full line height below it with an 8px gap
            int line1Y = boardHeight / 2 - goFm.getHeight() / 2 - 8;
            int line2Y = line1Y + goFm.getHeight() + 8;

            // Draw a dark-red shadow offset 4px right and 4px down
            // This gives the text a raised, retro 3-D look
            g2d.setColor(new Color(80, 0, 0));
            g2d.drawString(line1, line1X + 4, line1Y + 4);
            g2d.drawString(line2, line2X + 4, line2Y + 4);

            // Draw "GAME" with a vertical gradient from red (top) to yellow (bottom),
            // matching the retro pixel art style in the reference image.
            // GradientPaint maps from the top of the ascent (red) to the baseline (yellow)
            java.awt.GradientPaint gp1 = new java.awt.GradientPaint(
                0, line1Y - goFm.getAscent(), new Color(255, 60, 0),  // top = red-orange
                0, line1Y, new Color(255, 220, 0));                    // bottom = yellow
            g2d.setPaint(gp1);
            g2d.drawString(line1, line1X, line1Y);

            // Same gradient for "OVER", calculated from its own Y position
            java.awt.GradientPaint gp2 = new java.awt.GradientPaint(
                0, line2Y - goFm.getAscent(), new Color(255, 60, 0),
                0, line2Y, new Color(255, 220, 0));
            g2d.setPaint(gp2);
            g2d.drawString(line2, line2X, line2Y);

            // --- Final Score ---
            // Smaller pixel font (14pt) for the score line below "OVER"
            Font scoreFont = getPixelFont(14f);
            g2d.setFont(scoreFont);
            FontMetrics sFm = g2d.getFontMetrics(scoreFont);
            String scoreStr = "Score: " + (int) score;

            // Center the score text horizontally
            int scoreX = (boardWidth - sFm.stringWidth(scoreStr)) / 2;
            // Place it one heading-height below "OVER", plus a small 12px gap
            int scoreY = line2Y + goFm.getHeight() + 12;

            // Draw a black shadow 2px offset, then white text on top
            g2d.setColor(Color.BLACK);
            g2d.drawString(scoreStr, scoreX + 2, scoreY + 2);
            g2d.setColor(Color.WHITE);
            g2d.drawString(scoreStr, scoreX, scoreY);

            // --- High Score ---
            // Shows the best score achieved across all rounds this session,
            // displayed in gold below the current score
            String highStr = "Best: " + (int) highScore;
            int highX = (boardWidth - sFm.stringWidth(highStr)) / 2;
            int highY = scoreY + sFm.getHeight() + 10; // 10px gap below the score line

            // Gold shadow then bright gold text
            g2d.setColor(new Color(120, 80, 0));
            g2d.drawString(highStr, highX + 2, highY + 2);
            g2d.setColor(new Color(255, 215, 0)); // gold colour
            g2d.drawString(highStr, highX, highY);

            // --- Restart Hint ---
            // Tiny 8pt pixel font for the "SPACE to restart" prompt at the bottom
            Font hintFont = getPixelFont(8f);
            g2d.setFont(hintFont);
            FontMetrics hFm = g2d.getFontMetrics(hintFont);
            String hint = "SPACE to restart";

            // Center horizontally, place below the high score line
            int hintX = (boardWidth - hFm.stringWidth(hint)) / 2;
            int hintY = highY + sFm.getHeight() + 16;
            g2d.setColor(new Color(200, 200, 200)); // light grey
            g2d.drawString(hint, hintX, hintY);

        } else {
            // --- LIVE SCORE (during gameplay) ---

            // 18pt pixel font for the score shown top-left while playing
            Font scoreFont = getPixelFont(18f);
            g2d.setFont(scoreFont);
            String scoreStr = String.valueOf((int) score);

            // Draw a black copy 2px offset to act as a drop shadow,
            // then draw the white score on top so it's readable over any background
            g2d.setColor(Color.BLACK);
            g2d.drawString(scoreStr, 12, 38);
            g2d.setColor(Color.WHITE);
            g2d.drawString(scoreStr, 10, 36);
        }

        // --- MUTE BUTTON (always drawn, top-right corner) ---
        // Uses 8pt pixel font to match the game's retro style.
        // The button label switches between "SFX:ON" and "SFX:OFF" depending on state.
        Font muteFont = getPixelFont(8f);
        g2d.setFont(muteFont);
        FontMetrics mFm = g2d.getFontMetrics(muteFont);
        String muteLabel = soundMuted ? "SFX:OFF" : "SFX:ON";

        int btnPadX = 6, btnPadY = 4;
        int btnW = mFm.stringWidth(muteLabel) + btnPadX * 2;
        int btnH = mFm.getHeight() + btnPadY * 2;
        int btnX = boardWidth - btnW - 8; // 8px from right edge
        int btnY = 8;                     // 8px from top edge

        // Save button bounds so mouseClicked() can detect whether a click landed on it
        muteButtonBounds.setBounds(btnX, btnY, btnW, btnH);

        // Background: dark red when muted, dark grey when active
        g2d.setColor(soundMuted ? new Color(140, 30, 30, 210) : new Color(30, 30, 30, 210));
        g2d.fillRect(btnX, btnY, btnW, btnH);

        // Border: bright red when muted, light grey when active
        g2d.setColor(soundMuted ? new Color(255, 80, 80) : new Color(180, 180, 180));
        g2d.drawRect(btnX, btnY, btnW, btnH);

        // Label text
        g2d.setColor(soundMuted ? new Color(255, 100, 100) : Color.WHITE);
        g2d.drawString(muteLabel, btnX + btnPadX, btnY + btnPadY + mFm.getAscent());
    }



    public void move(){
        //bird
        velocityY += gravity;
        bird.y += velocityY;
        bird.y = Math.max(bird.y, 0);

        //pipees
        for(int i =0; i<pipes.size(); i++){
            Pipe pipe = pipes.get(i);
            pipe.x += velocityX;

            if(!pipe.passed && bird.x > pipe.x + pipe.width){
                pipe.passed = true;
                score += 0.5;
            }

            // Play the ding when the bird's center X reaches the pipe's center X.
            // Only fires for the top pipe (pipe.y < 0) so it plays once per pair,
            // not twice (once for top, once for bottom).
            // bird center X  = bird.x + bird.width / 2
            // pipe center X  = pipe.x + pipe.width / 2
            int birdCenterX = bird.x + bird.width / 2;
            int pipeCenterX = pipe.x + pipe.width / 2;
            if(!pipe.soundPlayed && pipe.y < 0 && birdCenterX >= pipeCenterX){
                pipe.soundPlayed = true;
                if(!soundMuted) playPointSound(); // respect mute setting
            }

            if(collision(bird, pipe)){
                gameOver = true;
            }
        }

        if(bird.y > boardHeight - 10){
            gameOver = true;
        }
    }

    public boolean collision(Bird a, Pipe b){
       return a.x < b.x + b.width && //a's top right corner doesnt reach b's top right corner
              a.x + a.width > b.x && //a's top right corner passes b's top left corner
              a.y < b.y + b.height && //a's top left corner doesn't reach b's bottom left corner
              a.y + a.height > b.y; //a's bottom left corner passes b's top left corner
    }


    @Override
    public void actionPerformed(ActionEvent e) {
        move();
        repaint(); //for every frame this will run , i.e 1000/60s
        if(gameOver){
            // Update high score if this round beats the previous best
            if(score > highScore){
                highScore = score;
            }
            // Play the fail sound only once (not every frame after game ends)
            if(!gameOverSoundPlayed){
                if(!soundMuted) playGameOverSound(); // respect mute setting
                gameOverSoundPlayed = true;
            }
            placePipesTimer.stop();
            gameLoop.stop();
        }
    }


    @Override
    public void keyPressed(KeyEvent e) {
        if(e.getKeyCode() == KeyEvent.VK_SPACE){
            if(gameOver){
                //restart the game by resetting conditions
                bird.y = birdY;
                velocityY = -9; // give the bird an immediate flap so it doesn't fall from the start
                pipes.clear();
                score = 0;
                gameOver = false;
                // Reset flag so the game-over sound can fire again next round
                gameOverSoundPlayed = false;
                gameLoop.start();
                placePipesTimer.start();
            } else {
                // Normal flap during gameplay
                velocityY = -9;
            }
        }
    }
    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public void keyReleased(KeyEvent e) {}

    // --- MouseListener ---

    @Override
    public void mouseClicked(MouseEvent e) {
        // If the click lands inside the mute button rectangle, toggle mute
        if(muteButtonBounds.contains(e.getPoint())){
            soundMuted = !soundMuted;
            repaint(); // redraw immediately so the button label updates
        }
    }

    // The remaining MouseListener methods are required by the interface but unused
    @Override public void mousePressed(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
}
