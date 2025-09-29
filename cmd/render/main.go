package main

import (
	"github.com/llgcode/draw2d/draw2dimg"
	"log"
	"log/slog"
	"os"
	"othellocord/app"
)

const pathBoard = "test_board.png"
const pathDisc = "test_disc.png"

func main() {
	board := app.InitialBoard()
	tiles := board.FindCurrentMoves()

	var moves []app.RankTile
	for i, tile := range tiles {
		d := -8 * (i % 2)
		moves = append(moves, app.RankTile{Tile: tile, H: float64(4 + d)})
	}

	rc := app.MakeRenderCache()
	imgBoard := rc.DrawBoardAnalysis(app.InitialBoard(), moves)

	if err := os.Remove(pathBoard); err != nil {
		slog.Error("failed to remove file", "err", err)
	}
	if err := draw2dimg.SaveToPngFile(pathBoard, imgBoard); err != nil {
		log.Fatalf("failed to save png file: %v", err)
	}

	imgDisc := app.DrawDisc(app.WhiteFill, 1)

	if err := os.Remove(pathDisc); err != nil {
		slog.Error("failed to remove file", "err", err)
	}
	if err := draw2dimg.SaveToPngFile(pathDisc, imgDisc); err != nil {
		log.Fatalf("failed to save png file: %v", err)
	}
}
