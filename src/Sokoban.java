import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.util.*;

import java.util.zip.*;
import java.awt.image.*;
import javax.imageio.*;
import java.io.*;


public class Sokoban extends JFrame{
    
    // threading variables
    private AppThread appthread; // thread for GUI
    private TimerThread timerthread; // thread for timer
    private boolean alreadyRan; // if a level was previously loaded
    
    // frame-related variables
    private SPanel container; // contains the playing field
    private JPanel containerTop; // container for top pane
    private JPanel containerRight; // container for undo button
    private Dimension window; // size of the window
    private Dimension screen; // resolution of user's screen
    private int tile; // tile size
    private boolean isReady; // stops repaint till constructor finishes
    private ActionCall action; // calls ActionCall for user interaction
    private Object call; // holds object that calls ActionCall
    private BufferedImage[] sprites; // holds any user supplied tile images
    private boolean imgRender; // determines if image tiles were used
    
    // level file variables
    private String fileName; // location of mapfile
    private LevelReader reader; // reads level info
    private int levels; // number of levels loaded
    
    // current level variables
    private int current; // current level
    private int width; // width of current level
    private int height; // height of current level
    private int pad; // padding for levels smaller than window width
    private String desc; // description of current level //****unnecessary
    private Contents[][] grid; // holds current level layout
    private int boxLeft; // number of boxes not on goals
    
    // movement variables
    private int[] keys; // acceptable key presses
    private int[] player; // stores position of player as (x, y)
    private int[] move; // the tile to be moved to
    private int moveX; // movement x-coordinate offset
    private int moveY; // movement y-coordinate offset
    private Contents playerTile; // holds the tile of the player
    private Contents moveTile;  // holds the tile the player is trying to go to
    private Contents pushTile; // holds the tile the box is pushing towards
    private int pushX; // x-coordinate of tile box is being pushed to
    private int pushY; // y-coordinate of tile box is being pushed to
    
    // menubar variables
    private JMenuBar bar; // the menubar
    private JMenu menuFile; // file menu
    private JMenuItem itemOpen; // open file
    private JMenuItem itemChoose; // choose level
    private JMenuItem itemLoad; // load image tiles
    private JMenuItem itemExit; // exit game
    
    // undo function variables
    private JButton undo; // the undo button
    private boolean undoReady; // if undo function is allowed
    private int[][] undoMoves; // stores prior location of tiles
    private Contents[] undoTiles; // stores prior state of tiles
    private boolean undoPush; // if undo includes a box push event
    
    // counter variables
    private JLabel count; // shows steps taken by user
    private int counter; // holds steps
    
    // timer variables
    private JLabel time; // shows time elapsed
    private int timer; // holds time in seconds
    private int h, m, s; // holds the hours, mins, secs respectively
    
    // thread for timer
    private class TimerThread extends Thread{
        
        private boolean stop;
        
        // allows thread to terminate without further iterations
        // NOTE: this method is implemented instead of stop() because stop() is
        // deprecated and considered buggy
        private void terminate(){
            stop = true;
        }
        
        // updates timer with time elapsed
        public void run(){
            while (true){
                try{
                    Thread.sleep(1000);
                    if (stop) return;
                    timer++;
                    time.setText(setTime());
                }
                catch (InterruptedException e){}
            }
        }
    }
    
    // GUI thread
    private class AppThread extends Thread{
        
        // runs GUI
        public void run(){
            initApp();
        }
    }
    
    // panel which contains the playing field
    private class SPanel extends JPanel{
        
        // chooses colour of tile based on type of tile
        private Color chooseColour(Contents c){
            switch (c){
                case GOAL:
                    return new Color(255, 215, 0); // gold
                case PLAYER:
                    return Color.BLUE;
                case PLAYERONGOAL:
                    return new Color(0, 191, 255); // light blue
                case WALL:
                    return Color.BLACK;
                case BOX:
                    return new Color(139, 69, 19); // brown
                case BOXONGOAL:
                    return new Color(222, 184, 135); // light brown
                default:
                    return Color.WHITE;
            }
        }
        
        // chooses shape of tile based on type of tile
        private Shape chooseShape(Contents c, int x, int y){
            switch (c){
                case GOAL:
                case PLAYER:
                case PLAYERONGOAL:
                    return new Ellipse2D.Double(x * tile + pad, y * tile, tile,
                        tile);
                case WALL:
                case BOX:
                case BOXONGOAL:
                default:
                    return new Rectangle.Double(x * tile + pad, y * tile, tile,
                        tile);
            }
        }
        
        // selects which tile paintComponent should draw next
        private BufferedImage chooseTile(Contents c){
            switch (c){
                case WALL:
                    return sprites[1];
                case BOX:
                    return sprites[2];
                case BOXONGOAL:
                    return sprites[3];
                case GOAL:
                    return sprites[4];
                case PLAYER:
                    return sprites[(moveX != 0) ? 6 + moveX : 7 + moveY];
                case PLAYERONGOAL:
                    return sprites[(moveX != 0) ? 10 + moveX : 11 + moveY];
                default:
                    return sprites[0];
            }
            
        }
        
        // fills panel with level tiles, either primitive shapes or images
        public void paintComponent(Graphics g1){
            if (isReady){
                super.paintComponent(g1);
                Graphics2D g = (Graphics2D)g1;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                if (imgRender)
                    for (int x = 0; x < width; x++)
                        for (int y = 0; y < height; y++)
                            g.drawImage(chooseTile(grid[x][y]), x * tile + pad,
                                y * tile, null);
                else
                    for (int x = 0; x < width; x++)
                        for (int y = 0; y < height; y++){
                            g.setColor(chooseColour(grid[x][y]));
                            g.fill(chooseShape(grid[x][y], x, y));
                    }
            }
        }
    }
    
    // class for monitoring key presses
    private class KeyTracker implements KeyListener{
        
        private int key;
        
        // reacts to user pressing arrow keys or N, R, U
        public void keyPressed(KeyEvent k){
            key = k.getKeyCode();
            // if key pressed is one of arrow keys or N, R, U
            if (Arrays.binarySearch(keys, key) > -1){
                if (key == 78){
                    if (current == levels - 1){
                        dialogBox("Error", "No more levels available.");
                        return;
                    }
                    boxLeft = 0;
                }
                else if (key == 82){
                    boxLeft = 0;
                    current--;
                }
                else if (key == 85){
                    if (undoReady) eventUndo();
                }
                else{
                    moveX = (key == 37) ? -1 : (key == 39) ? 1 : 0;
                    moveY = (key == 38) ? -1 : (key == 40) ? 1 : 0;
                    playerTile = grid[player[0]][player[1]];
                    move = new int[]{player[0] + moveX, player[1] + moveY};
                    moveTile = grid[move[0]][move[1]];
                    switch (moveTile){
                        case WALL:
                            break;
                        case BOX:
                        case BOXONGOAL:
                            pushX = move[0] + moveX;
                            pushY = move[1] + moveY;
                            pushTile = grid[pushX][pushY];
                            if (pushTile == Contents.EMPTY || pushTile ==
                                Contents.GOAL){
                                if (pushTile == Contents.EMPTY){
                                    grid[pushX][pushY] = Contents.BOX;
                                    if (moveTile == Contents.BOXONGOAL)
                                        boxLeft++;
                                }
                                else{
                                    grid[pushX][pushY] = Contents.BOXONGOAL;
                                    if (moveTile == Contents.BOX) boxLeft--;
                                }
                                grid[move[0]][move[1]] = (moveTile ==
                                    Contents.BOX) ? Contents.PLAYER :
                                    Contents.PLAYERONGOAL;
                                grid[player[0]][player[1]] = (playerTile ==
                                    Contents.PLAYERONGOAL) ? Contents.GOAL :
                                    Contents.EMPTY;
                                undoSetup(true);
                                player = move;
                            }
                            break;
                        default:
                            // despite looking very similar to the branch of
                            // code above, it would take many if statements to
                            // concatenate the two branches of code, so this
                            // really is the more optimized way
                            grid[move[0]][move[1]] = (moveTile == Contents.GOAL)
                                ? Contents.PLAYERONGOAL : Contents.PLAYER;
                            grid[player[0]][player[1]] = (playerTile ==
                                Contents.PLAYERONGOAL) ? Contents.GOAL :
                                Contents.EMPTY;
                            undoSetup(false);
                            player = move;
                    }
                }
                eventCheck();
            }
        }
        
        public void keyReleased(KeyEvent k){}
        public void keyTyped(KeyEvent k){}
    }
    
    // does different functions based on button or menuitem press
    private class ActionCall implements ActionListener{
        
        // called when a button or menuitem is pressed
        public void actionPerformed(ActionEvent e){
            call = e.getSource();
            if (call == undo){
                container.requestFocus();
                eventUndo();
            }
            else if (call == itemOpen) openBox();
            else if (call == itemChoose) chooseBox();
            else if (call == itemLoad) loadBox();
            else System.exit(0);
        }
    }
    
    // allows user to choose mapfile and starts the app
    public Sokoban(){
        startThread();
    }
    
    // sets mapfile as fileName and starts the app
    public Sokoban(String fileName){
        this.fileName = fileName;
        startThread();
        // forces level initialization to hold till app loads
        while(true)
            if(!appthread.isAlive()){
                initFirstLevel();
                return;
            }
    }
    
    // starts GUI thread
    // NOTE: this is a separate function because starting threads inside a
    // constructor is considered unsafe
    private void startThread(){
        appthread = new AppThread();
        appthread.start();
    }
    
    // initializes the application ans starts first level
    private void initApp(){
        container = new SPanel();
        //current = 0;
        tile = 30;
        // ensures all threads terminate when window is closed
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false); // fixes window dimensions
        setVisible(true);
        setLayout(new BorderLayout());
        action = new ActionCall();
        bar = new JMenuBar();
        menuFile = new JMenu("File");
        itemOpen = new JMenuItem("Open File");
        itemChoose = new JMenuItem("Choose Level");
        itemLoad = new JMenuItem("Load Tiles");
        itemExit = new JMenuItem("Exit");
        bar.add(menuFile);
        itemOpen.addActionListener(action);
        itemChoose.addActionListener(action);
        itemChoose.setEnabled(false);
        itemLoad.addActionListener(action);
        itemExit.addActionListener(action);
        menuFile.add(itemOpen);
        menuFile.add(itemChoose);
        menuFile.add(itemLoad);
        menuFile.add(itemExit);
        setJMenuBar(bar);
        container.setBackground(Color.WHITE);
        container.addKeyListener(new KeyTracker());
        container.setFocusable(true);
        containerTop = new JPanel();
        containerTop.setLayout(new GridLayout(1,3));
        count = new JLabel();
        count.setBorder(BorderFactory.createEtchedBorder());
        containerTop.add(count);
        time = new JLabel("", JLabel.CENTER);
        time.setBorder(BorderFactory.createEtchedBorder());
        containerTop.add(time);
        undo = new JButton("Undo");
        undo.addActionListener(action);
        undo.setPreferredSize(new Dimension(70, 26));
        undo.setEnabled(false);
        containerRight = new JPanel();
        containerRight.add(undo);
        containerTop.add(containerRight);
        keys = new int[]{37, 38, 39, 40, 78, 82, 85};
        undoMoves = new int[3][2];
        undoTiles = new Contents[3];
        // adds step counter, timer, and undo button at top of playing field
        add(containerTop, BorderLayout.PAGE_START);
        container.setPreferredSize(new Dimension(240, 240));
        setTitle("SOKOBAN");
        screen = Toolkit.getDefaultToolkit().getScreenSize();
        // adds playing field to the main portion of the frame
        add(container, BorderLayout.CENTER);
        // centers window on screen, 59 is containerTop + bar's height
        setLocation((screen.width - 240) / 2, (screen.height - 240 - 59) / 2);
        container.setEnabled(false);
        pack();
    }
    
    // initializes levelreader and sets up interface before starting 1st level
    private void initFirstLevel(){
        reader = new LevelReader();
        levels = reader.readLevels(fileName);
        current = 0;
        itemChoose.setEnabled(true);
        container.setEnabled(true);
        if (alreadyRan) timerthread.terminate();
        initLevel();
        // enables repaint function
        isReady = true;
        alreadyRan = true;
    }
    
    // initializes the current level
    private void initLevel(){
        width = reader.getWidth(current);
        height = reader.getHeight(current);
        desc = reader.getDescription(current);
        window = new Dimension(width * tile, height * tile);
        container.setPreferredSize(window);
        setTitle("SOKOBAN Level: " + desc);
        add(container, BorderLayout.CENTER);
        setLocation((screen.width - Math.max(240, window.width)) / 2,
            (screen.height - window.height - 59) / 2);
        pack();
        // padding added to levels smaller than window width
        pad = (container.getWidth() - window.width) / 2;
        container.setEnabled(true);
        container.requestFocus();
        undoReady = false;
        undo.setEnabled(false);
        counter = 0;
        count.setText("Steps: " + counter);
        timer = 0;
        time.setText("00:00:00");
        grid = new Contents[width][height];
        boxLeft = 0;
        // reads current level into array
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++){
                grid[x][y] = reader.getTile(current, x, y);
                if (reader.getTile(current, x, y) == Contents.PLAYER ||
                    reader.getTile(current, x, y) == Contents.PLAYERONGOAL)
                    player = new int[]{x, y};
                if (reader.getTile(current, x, y) == Contents.BOX)
                    boxLeft++;
            }
        timerthread = new TimerThread();
        timerthread.start();
    }
    
    // allows user to select a zip file with tile images and parses files into
    // image objects
    private void loadBox(){
        JFileChooser file = new JFileChooser();
        file.showOpenDialog(this);
        try{
            ZipFile zip = new ZipFile(file.getSelectedFile().getAbsolutePath());
            sprites = new BufferedImage[13];
            BufferedInputStream input = null;
            for (int x = 0; x < 13; x++){
                input = new BufferedInputStream(zip.getInputStream(zip.getEntry
                    (x + ".png")));
                sprites[x] = ImageIO.read(input);
            }
            zip.close();
            input.close();
            imgRender = true;
            repaint();
        }
        catch (IOException e){
            dialogBox("Error", "You did not select a valid zip file.");
        }
        catch (NullPointerException e){}
    }
    
    // formats elapsed time for label to show
    private String setTime(){
        s = timer % 60;
        m = timer / 60 % 60;
        h = timer / 60 / 60;
        return "" + ((h < 10) ? "0" + h : h) + ":" + ((m < 10) ? "0" + m : m)
            + ":" + ((s < 10) ? "0" + s : s);
    }
    
    // when a move is successfully made, records initial state of changed tiles
    private void undoSetup(boolean undoPush){
        count.setText("Steps: " + ++counter);
        this.undoPush = undoPush;
        undo.setEnabled(true);
        undoReady = true;
        undoMoves[0] = player;
        undoTiles[0] = playerTile;
        undoMoves[1] = move;
        undoTiles[1] = moveTile;
        if (undoPush){
            undoMoves[2] = new int[]{pushX, pushY};
            undoTiles[2] = pushTile;
        }
    }
    
    // when user calls undo, puts tiles to previous state
    private void eventUndo(){
        grid[undoMoves[0][0]][undoMoves[0][1]] = undoTiles[0];
        player = undoMoves[0];
        grid[undoMoves[1][0]][undoMoves[1][1]] = undoTiles[1];
        if (undoPush){
            grid[undoMoves[2][0]][undoMoves[2][1]] = undoTiles[2];
            if (moveTile == Contents.BOXONGOAL && pushTile == Contents.EMPTY)
                boxLeft--;
            if (moveTile == Contents.BOX && pushTile == Contents.GOAL)
                boxLeft++;
        }
        count.setText("Steps: " + --counter);
        undo.setEnabled(false);
        undoReady = false;
        undoPush = false;
        repaint();
    }
    
    // checks if all boxes are on goals and restarts current level or starts
    // new level
    private void eventCheck(){
        if (boxLeft == 0){
            timerthread.terminate();
            if (++current == levels){
                eventWin();
                return;
            }
            initLevel();
        }
        repaint();
    }
    
    // if user finishes all levels, display dialog then disable game
    private void eventWin(){
        current--;
        undo.setEnabled(false);
        container.setEnabled(false);
        dialogBox("Congratulations!", "You have finished all the levels.");
    }
    
    // shows dialog box with message
    private void dialogBox(String title, String message){
        JOptionPane.showMessageDialog(this, message, title,
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    // shows an open dialog box and renders the level file
    private void openBox(){
        JFileChooser file = new JFileChooser();
        file.showOpenDialog(this);
        try{
            fileName = file.getSelectedFile().getAbsolutePath();
            if (!alreadyRan)
                if (JOptionPane.showOptionDialog(this, "Would you like to load "
                    + "image tiles from a zip file?", "Load Image Tiles",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, new String[]{"Yes", "No"}, null) == 0)
                    loadBox();
            initFirstLevel();
        }
        catch (NullPointerException e){}
    }
    
    // shows a dialog to choose a level and renders the level
    private void chooseBox(){
        String[] options = new String[levels];
        for (int x = 0; x < levels; x++)
            options[x] = reader.getDescription(x);
        String option = (String)JOptionPane.showInputDialog(this,
            "Choose a level from the list below:", "Choose Level",
            JOptionPane.PLAIN_MESSAGE, null, options, options[current]);
        if (option != null){
            for (int x = 0; x < levels; x++)
                if (option.equals(options[x]))
                    current = x - 1; // one less because eventCheck adds 1
            boxLeft = 0;
            eventCheck();
        }
    }
    
    // starts a new instance of game
    public static void main(String[] args){
        
        //JFrame frame = new Sokoban("C:\\Users\\Jawad\\Desktop\\SOKOBAN\\src\\s11.txt");
        JFrame frame = new Sokoban();
    }
}