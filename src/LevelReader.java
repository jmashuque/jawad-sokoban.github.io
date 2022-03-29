
/**
 * The LevelReader class for the CCPS109 programming project. The students should not modify this class.
 * This class reads in the given level file, and can then be asked by the student code to give the
 * contents of the given level.
 * 
 * Level files taken from http://users.bentonrea.com/~sasquatch/sokoban/
 * 
 * @author Ilkka Kokkarinen
 * @version Dec 10 2010
 */
import java.util.*;
import java.io.*;

public class LevelReader {
    private ArrayList<Level> levels;
    
    private Contents convert(char c) {
        if(c == '#') return Contents.WALL;
        if(c == '@') return Contents.PLAYER;
        if(c == '$') return Contents.BOX;
        if(c == '.') return Contents.GOAL;
        if(c == '*') return Contents.BOXONGOAL;
        if(c == '+') return Contents.PLAYERONGOAL;        
        return Contents.EMPTY;
    }

    private class Level {
        private int width = 0;
        private ArrayList<String> rows = new ArrayList<String>();
        private String description = "";
        
        public void addRow(String row) {
            rows.add(row);
            if(row.length() > width) { width = row.length(); }
        }
        
        public void setDescription(String desc) {
            description = desc;
        }
        
        public int getWidth() { return width; }
        public int getHeight() { return rows.size(); }
        public String getDescription() { return description; }
        
        public Contents getCell(int x, int y) {
            String row = rows.get(y);
            if(x >= row.length()) return Contents.EMPTY;
            else return convert(row.charAt(x));             
        }
    }

    int readLevels(String fileName) {
        levels = new ArrayList<Level>();
        Level currentLevel = new Level();
        String description = "";
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(fileName));
            String line = br.readLine();
            while(line != null) {
                if(line.trim().equals("")) {
                    line = br.readLine();
                    continue;
                }
                if(line.startsWith(";")) {
                    if(currentLevel.getHeight() > 0) {
                        levels.add(currentLevel);
                        currentLevel = new Level();
                    }
                    currentLevel.setDescription(line.substring(1).trim());                    
                }
                else {
                    currentLevel.addRow(line);
                }
                line = br.readLine();
            }
            levels.add(currentLevel);
        }
        catch(IOException e) {
            System.out.println("Error reading level file!");
        }
        finally { try { if(br != null) { br.close(); }} catch(IOException e) { } }
        return levels.size();
    }

    public int getHeight(int level) { return levels.get(level).getHeight(); }
    public int getWidth(int level) { return levels.get(level).getWidth(); }
    public String getDescription(int level) { return levels.get(level).getDescription(); }
    public Contents getTile(int level, int x, int y) { 
        return levels.get(level).getCell(x,y);
    }
}