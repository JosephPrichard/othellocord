/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.renderers;

import othello.OthelloBoard;
import othello.Tile;
import utils.Image;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OthelloBoardRenderer
{
    private static final int DISC_SIZE = 100;
    private static final int LINE_THICKNESS = 4;
    private static final int SIDE_OFFSET = 40;
    private static final int TILE_SIZE = DISC_SIZE + LINE_THICKNESS;

    private static final int GREEN = new Color(82, 172, 85).getRGB();
    private static final int BLACK = new Color(0, 0, 0).getRGB();
    private static final Font font = new Font("Courier", Font.BOLD, 28);

    private final BufferedImage whiteDiscImage;
    private final BufferedImage blackDiscImage;
    private final BufferedImage whiteStarImage;
    private final BufferedImage blackStarImage;
    private final BufferedImage backgroundImage;

    public OthelloBoardRenderer() {
        whiteDiscImage = Image.readResizedImage("images/white_disc.png", DISC_SIZE);
        blackDiscImage = Image.readResizedImage("images/black_disc.png", DISC_SIZE);
        whiteStarImage = Image.readResizedImage("images/white_star.png", DISC_SIZE);
        blackStarImage = Image.readResizedImage("images/black_star.png", DISC_SIZE);
        backgroundImage = drawBackground(OthelloBoard.getBoardSize());
    }

    private static BufferedImage drawBackground(int boardSize) {
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

        var g = image.getGraphics();

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

        g.dispose();

        return image;
    }

    public BufferedImage drawBoard(OthelloBoard board) {
        return drawBoard(board, new ArrayList<>());
    }

    public BufferedImage drawBoardMoves(OthelloBoard board) {
        return drawBoard(board, board.findPotentialMoves());
    }

    public BufferedImage drawBoard(OthelloBoard board, List<Tile> moves) {
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
        var moveImage = board.isBlackMove() ? blackStarImage : whiteStarImage;
        for (var move : moves) {
            var x = SIDE_OFFSET + LINE_THICKNESS + move.getCol() * TILE_SIZE;
            var y = SIDE_OFFSET + LINE_THICKNESS + move.getRow() * TILE_SIZE;
            g.drawImage(moveImage, x, y, null);
        }

        g.dispose();
        return boardImage;
    }

    private void drawDiscs(Graphics boardGraphics, OthelloBoard board) {
        // draw discs onto board, either empty, black, or white
        for (var tile : board.tiles()) {
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

        var renderer = new OthelloBoardRenderer();

        var moves = board.findPotentialMoves();

        var image = renderer.drawBoard(board, moves);

        var outputFile = new File("test_board.png");
        ImageIO.write(image, "png", outputFile);
    }
}
