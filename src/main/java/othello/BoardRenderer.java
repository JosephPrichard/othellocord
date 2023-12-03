/*
 * Copyright (c) Joseph Prichard 2023.
 */

package othello;

import utils.Image;

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
    private static final Font font = new Font("Courier", Font.BOLD, 28);
    private static final Color OUTLINE = new Color(40, 40, 40);
    private static final Color BLACK_FILL = new Color(20, 20, 20);
    private static final Color WHITE_FILL = new Color(250, 250, 250);
    private static final Color NO_FILL = new Color(255, 255, 255, 0);
    private static final int[][] DOT_LOCATIONS = {{2, 2}, {6, 6}, {2, 6}, {6, 2}};
    private static final int DOT_SIZE = 16;
    private static final int TOP_LEFT = 5;

    private static final BufferedImage whiteDiscImage = drawDisc(WHITE_FILL);;
    private static final BufferedImage blackDiscImage = drawDisc(BLACK_FILL);
    private static final BufferedImage outlineImage = drawDisc(NO_FILL);
    private static final BufferedImage backgroundImage = drawBackground(OthelloBoard.getBoardSize());

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
            var rect = new Rectangle(SIDE_OFFSET + i * TILE_SIZE, 0, TILE_SIZE, SIDE_OFFSET);
            Image.drawCenteredString(g, text, rect, font);
        }

        // draw numbers on vertical sidebar
        for (var i = 0; i < boardSize; i++) {
            var text = Integer.toString(i + 1);
            var rect = new Rectangle(0, SIDE_OFFSET + i * TILE_SIZE, SIDE_OFFSET, TILE_SIZE);
            Image.drawCenteredString(g, text, rect, font);
        }
    }

    private static BufferedImage drawDisc(Color fillColor) {
        var image = new BufferedImage(DISC_SIZE, DISC_SIZE, BufferedImage.TYPE_INT_ARGB);

        var graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(fillColor);
        graphics.fillOval(TOP_LEFT, TOP_LEFT, DISC_SIZE - TOP_LEFT * 2, DISC_SIZE - TOP_LEFT * 2);
        graphics.setColor(OUTLINE);
        graphics.drawOval(TOP_LEFT, TOP_LEFT, DISC_SIZE - TOP_LEFT * 2, DISC_SIZE - TOP_LEFT * 2);
        graphics.dispose();

        return image;
    }

    public static BufferedImage drawBoard(OthelloBoard board) {
        return drawBoard(board, new ArrayList<>());
    }

    public static BufferedImage drawBoardMoves(OthelloBoard board) {
        return drawBoard(board, board.findPotentialMoves());
    }

    public static BufferedImage drawBoard(OthelloBoard board, List<Tile> moves) {
        var boardImage = new BufferedImage(
            backgroundImage.getWidth(),
            backgroundImage.getHeight(),
            backgroundImage.getType()
        );
        var g = boardImage.getGraphics();

        // draw background image and discs onto board
        g.drawImage(backgroundImage, 0, 0, null);
        drawDiscs(g, board);

        // draw each move image onto the board
        for (var move : moves) {
            var x = SIDE_OFFSET + LINE_THICKNESS + move.getCol() * TILE_SIZE;
            var y = SIDE_OFFSET + LINE_THICKNESS + move.getRow() * TILE_SIZE;
            g.drawImage(outlineImage, x, y, null);
        }

        g.dispose();
        return boardImage;
    }

    private static void drawDiscs(Graphics boardGraphics, OthelloBoard board) {
        // draw discs onto board, either empty, black, or white
        for (var tile : OthelloBoard.tiles()) {
            var x = SIDE_OFFSET + LINE_THICKNESS + tile.getCol() * TILE_SIZE;
            var y = SIDE_OFFSET + LINE_THICKNESS + tile.getRow() * TILE_SIZE;
            // determine which bitmap belongs in the disc slot
            int color = board.getSquare(tile);
            if (color == OthelloBoard.BLACK) {
                boardGraphics.drawImage(blackDiscImage, x, y, null);
            } else if (color == OthelloBoard.WHITE) {
                boardGraphics.drawImage(whiteDiscImage, x, y, null);
            }
        }
    }

    // test driver function to see board image
    public static void main(String[] args) throws IOException {
        var board = new OthelloBoard();

        var moves = board.findPotentialMoves();

        var image = drawBoard(board, moves);

        var outputFile = new File("test_board.png");
        ImageIO.write(image, "png", outputFile);
    }
}
