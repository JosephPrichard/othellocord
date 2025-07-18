package main

import (
	"github.com/llgcode/draw2d/draw2dimg"
	"log/slog"
	"os"
	"othellocord/app/othello"
)

const pathBoard = "test_board.png"
const pathDisc = "test_disc.png"

func main() {
	board := othello.InitialBoard()
	tiles := board.FindCurrentMoves()

	var moves []othello.Move
	for i, tile := range tiles {
		d := -8 * (i % 2)
		moves = append(moves, othello.Move{Tile: tile, H: float64(4 + d)})
	}

	rc := othello.NewRenderCache()
	imgBoard := othello.DrawBoardAnalysis(rc, othello.InitialBoard(), moves)

	if err := os.Remove(pathBoard); err != nil {
		slog.Error("failed to remove file", "error", err)
	}
	if err := draw2dimg.SaveToPngFile(pathBoard, imgBoard); err != nil {
		panic(err)
	}

	imgDisc := othello.DrawDisc(othello.WhiteFill, 1)

	if err := os.Remove(pathDisc); err != nil {
		slog.Error("failed to remove file", "error", err)
	}
	if err := draw2dimg.SaveToPngFile(pathDisc, imgDisc); err != nil {
		panic(err)
	}
}
