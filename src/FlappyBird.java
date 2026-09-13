import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;
import javax.swing.*;


public class FlappyBird extends JPanel implements ActionListener, KeyListener{
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

    Timer gameLoop;
    Timer placePipesTimer;

    FlappyBird(){
        setPreferredSize(new Dimension(boardWidth, boardHeight));
        //setBackground(Color.blue);
        setFocusable(true);
        addKeyListener(this);

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
        Pipe topPipe = new Pipe(topPipeImg);
        pipes.add(topPipe);
    }

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
        }
    }
    @Override
    public void actionPerformed(ActionEvent e) {
        move();
        repaint(); //for every frame this will run , i.e 1000/60s
    }


    @Override
    public void keyPressed(KeyEvent e) {
        if(e.getKeyCode() == KeyEvent.VK_SPACE){
            velocityY = -9;
        }
    }
    @Override
    public void keyTyped(KeyEvent e) {}


    @Override
    public void keyReleased(KeyEvent e) {}
}
