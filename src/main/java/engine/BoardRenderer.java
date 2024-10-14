/*
 * Copyright (c) Joseph Prichard 2023.
 */

package engine;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BoardRenderer {

    private static final int DISC_SIZE = 100;
    private static final int LINE_THICKNESS = 4;
    private static final int SIDE_OFFSET = 40;
    private static final int TILE_SIZE = DISC_SIZE + LINE_THICKNESS;

    private static final int GREEN = new Color(82, 172, 85).getRGB();
    private static final int BLACK = new Color(0, 0, 0).getRGB();
    private static final Font FONT = new Font("Courier", Font.BOLD, 28);
    private static final Color OUTLINE = new Color(40, 40, 40);
    private static final Color BLACK_FILL = new Color(20, 20, 20);
    private static final Color WHITE_FILL = new Color(250, 250, 250);
    private static final Color NO_FILL = new Color(255, 255, 255, 0);
    private static final int[][] DOT_LOCATIONS = {{2, 2}, {6, 6}, {2, 6}, {6, 2}};
    private static final int DOT_SIZE = 16;
    private static final int TOP_LEFT = 5;

    private static final BufferedImage WHITE_DISC_IMAGE = drawDisc(WHITE_FILL, new BasicStroke(1));
    private static final BufferedImage BLACK_DISC_IMAGE = drawDisc(BLACK_FILL, new BasicStroke(1));
    private static final BufferedImage OUTLINE_IMAGE = drawDisc(NO_FILL, new BasicStroke(2));
    private static final BufferedImage BACKGROUND_IMAGE = drawBackground(OthelloBoard.getBoardSize());

    public static BufferedImage drawBoard(OthelloBoard board) {
        return drawBoard(board, new ArrayList<>());
    }

    public static BufferedImage drawBoardMoves(OthelloBoard board) {
        return drawBoard(board, board.findPotentialMoves());
    }

    public static BufferedImage drawBoard(OthelloBoard board, List<Tile> moves) {
        var boardImage = new BufferedImage(BACKGROUND_IMAGE.getWidth(), BACKGROUND_IMAGE.getHeight(), BACKGROUND_IMAGE.getType());
        var g = drawBoardDiscs(board, boardImage);

        // draw each move image onto the board
        for (var move : moves) {
            var x = SIDE_OFFSET + LINE_THICKNESS + move.col() * TILE_SIZE;
            var y = SIDE_OFFSET + LINE_THICKNESS + move.row() * TILE_SIZE;
            g.drawImage(OUTLINE_IMAGE, x, y, null);
        }

        g.dispose();
        return boardImage;
    }

    public static BufferedImage drawBoardAnalysis(OthelloBoard board, List<Tile.Move> bestMoves) {
        var boardImage = new BufferedImage(BACKGROUND_IMAGE.getWidth(), BACKGROUND_IMAGE.getHeight(), BACKGROUND_IMAGE.getType());
        var g = drawBoardDiscs(board, boardImage);

        // draw each heuristic eval onto the board
        for (var i = 0; i < bestMoves.size(); i++) {
            var move = bestMoves.get(i);
            var tile = move.tile();

            var h = move.heuristic();
            var hText = Float.toString(h);
            var end = Math.min(hText.length(), h >= 0 ? 4 : 5);
            hText = hText.substring(0, end);

            if (i == 0) {
                g.setColor(Color.CYAN);
            } else {
                g.setColor(Color.YELLOW);
            }

            var x = SIDE_OFFSET + tile.col() * TILE_SIZE;
            var y = SIDE_OFFSET + tile.row() * TILE_SIZE;
            var rect = new Rectangle(x, y, TILE_SIZE, TILE_SIZE);
            drawCenteredString(g, hText, rect, FONT);
        }

        g.dispose();
        return boardImage;
    }

    private static Graphics drawBoardDiscs(OthelloBoard board, BufferedImage boardImage) {
        var g = boardImage.getGraphics();
        g.drawImage(BACKGROUND_IMAGE, 0, 0, null);

        // draw discs onto board, either empty, black, or white
        for (var tile : OthelloBoard.tiles()) {
            var x = SIDE_OFFSET + LINE_THICKNESS + tile.col() * TILE_SIZE;
            var y = SIDE_OFFSET + LINE_THICKNESS + tile.row() * TILE_SIZE;
            // determine which bitmap belongs in the tile slot
            int color = board.getSquare(tile);
            if (color == OthelloBoard.BLACK) {
                g.drawImage(BLACK_DISC_IMAGE, x, y, null);
            } else if (color == OthelloBoard.WHITE) {
                g.drawImage(WHITE_DISC_IMAGE, x, y, null);
            }
        }

        return g;
    }

    private static BufferedImage drawBackground(int boardSize) {
        var image = drawColoredBackground(boardSize);
        var g = image.getGraphics();
        drawBackgroundDots(g);
        drawBackgroundText(g, boardSize);
        g.dispose();
        return image;
    }

    private static BufferedImage drawColoredBackground(int boardSize) {
        var width = TILE_SIZE * boardSize + LINE_THICKNESS + SIDE_OFFSET;
        var height = TILE_SIZE * boardSize + LINE_THICKNESS + SIDE_OFFSET;

        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // drawing green background
        for (var x = SIDE_OFFSET; x < image.getWidth(); x++) {
            for (var y = SIDE_OFFSET; y < image.getHeight(); y++) {
                image.setRGB(x, y, GREEN);
            }
        }

        // draw black horizontal lines
        for (var i = 0; i < boardSize + 1; i++) {
            for (var x = SIDE_OFFSET; x < image.getWidth(); x++) {
                var y = i * TILE_SIZE + SIDE_OFFSET;
                for (var j = 0; j < LINE_THICKNESS; j++) {
                    image.setRGB(x, y + j, BLACK);
                }
            }
        }

        // draw black vertical lines
        for (var i = 0; i < boardSize + 1; i++) {
            for (var y = SIDE_OFFSET; y < image.getHeight(); y++) {
                var x = i * TILE_SIZE + SIDE_OFFSET;
                for (var j = 0; j < LINE_THICKNESS; j++) {
                    image.setRGB(x + j, y, BLACK);
                }
            }
        }
        return image;
    }

    private static void drawBackgroundDots(Graphics g) {
        g.setColor(BLACK_FILL);
        for (var location : DOT_LOCATIONS) {
            // fills an oval, the x,y is subtracted by the dot size and line thickness to center it
            var col = location[0];
            var row = location[1];
            var x = SIDE_OFFSET + col * TILE_SIZE - DOT_SIZE / 2 + LINE_THICKNESS / 2;
            var y = SIDE_OFFSET + row * TILE_SIZE - DOT_SIZE / 2 + LINE_THICKNESS / 2;
            g.fillOval(x, y, DOT_SIZE, DOT_SIZE);
        }

        g.setColor(WHITE_FILL);
    }

    private static void drawBackgroundText(Graphics g, int boardSize) {
        // draw letters on horizontal sidebar
        for (var i = 0; i < boardSize; i++) {
            var text = Character.toString(i + 'A');
            var x = SIDE_OFFSET + i * TILE_SIZE;
            var rect = new Rectangle(x, 0, TILE_SIZE, SIDE_OFFSET);
            drawCenteredString(g, text, rect, FONT);
        }

        // draw numbers on vertical sidebar
        for (var i = 0; i < boardSize; i++) {
            var text = Integer.toString(i + 1);
            var y = SIDE_OFFSET + i * TILE_SIZE;
            var rect = new Rectangle(0, y, SIDE_OFFSET, TILE_SIZE);
            drawCenteredString(g, text, rect, FONT);
        }
    }

    private static BufferedImage drawDisc(Color fillColor, Stroke stroke) {
        var image = new BufferedImage(DISC_SIZE, DISC_SIZE, BufferedImage.TYPE_INT_ARGB);

        var graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(fillColor);
        graphics.fillOval(TOP_LEFT, TOP_LEFT, DISC_SIZE - TOP_LEFT * 2, DISC_SIZE - TOP_LEFT * 2);
        graphics.setColor(OUTLINE);
        graphics.setStroke(stroke);
        graphics.drawOval(TOP_LEFT, TOP_LEFT, DISC_SIZE - TOP_LEFT * 2, DISC_SIZE - TOP_LEFT * 2);
        graphics.dispose();

        return image;
    }

    public static void drawCenteredString(Graphics g, String text, Rectangle rect, Font font) {
        var metrics = g.getFontMetrics(font);
        // Determine the X coordinate for the text
        var x = rect.x + (rect.width - metrics.stringWidth(text)) / 2;
        // Determine the Y coordinate for the text (note we add the ascent, as in java 2d 0 is top of the screen)
        var y = rect.y + ((rect.height - metrics.getHeight()) / 2) + metrics.getAscent();
        g.setFont(font);
        g.drawString(text, x, y);
    }

    // test driver function to see board image
    public static void main(String[] args) throws IOException {
        var board = OthelloBoard.initial();
        var image = drawBoardAnalysis(board, new OthelloAgent().findRankedMoves(board, 5));

        var outputFile = new File("test_board.png");
        ImageIO.write(image, "png", outputFile);
    }
}
