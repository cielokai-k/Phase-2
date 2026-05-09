package grtree;

import java.awt.*;
import java.awt.event.*;

/**
 *  Canvas on which to paint the tree.
 *  Does not work with Panel replacing Canvas.  ScrollPane needs
 *  to hold a component which is not derived from Container
 *  (apparently).
 *  
 *  The canvas needs to be as large as the tree so that a scrolling
 *  container could view ALL of the tree
 *
 */
public class TreeCanvas extends Canvas {
    private Tree tree;
    private double scale = 1.0; 
    
    public TreeCanvas(Tree t) { 
        tree = t; 
        this.setBackground(Color.white);
        
        this.addMouseWheelListener(new MouseWheelListener() {
            public void mouseWheelMoved(MouseWheelEvent e) {
                
                if (e.isControlDown()) {
                    double oldScale = scale;
                    
                    if (e.getWheelRotation() < 0) {
                        scale *= 1.1; // Zoom In
                    } else {
                        scale /= 1.1; // Zoom Out
                    }

                    if (scale < 0.2) scale = 0.2; // Floor (Max zoom out)
                    if (scale > 4.0) scale = 4.0; // Ceiling (Max zoom in)
                    
                    if (scale != oldScale) {
                        int newWidth = (int)(getWidth() * (scale / oldScale));
                        int newHeight = (int)(getHeight() * (scale / oldScale));
                        setSize(new Dimension(newWidth, newHeight));
                        
                        repaint(); // Redraw
                        
                        if (getParent() != null) {
                            getParent().doLayout(); // Update Scrollbars
                        }
                    }
                } else {
                    if (getParent() != null) {
                        getParent().dispatchEvent(e);
                    }
                }
            }
        });
    }

    public void setTree(Tree t) { tree = t; }

    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        g2d.scale(scale, scale);

        Dimension d = this.getSize();
        int w = tree.getTreeWidth(g2d); 

        int unscaledWidth = (int)(d.width / scale);
        tree.drawTree(g2d, (unscaledWidth - w)/2, 30);
    }     
}