package othello

import (
	_ "embed"
	"fmt"
	"github.com/golang/freetype/truetype"
	"github.com/llgcode/draw2d"
	"github.com/llgcode/draw2d/draw2dimg"
	"github.com/llgcode/draw2d/draw2dkit"
	"image"
	"image/color"
	"image/draw"
	"math"
	"strconv"
)

//go:embed fonts/Courier.ttf
var TtfFont []byte

const (
	DiscSize      = 100
	LineThickness = 4
	SideOffset    = 40
	TileSize      = DiscSize + LineThickness
	DotSize       = 8
	TopLeft       = 4
	SideFont      = 25.0
	AnalysisFont  = 23.0
)

var (
	GreenColor   = color.RGBA{R: 88, G: 184, B: 91, A: 255}
	BlackColor   = color.RGBA{R: 0, G: 0, B: 0, A: 255}
	CyanColor    = color.RGBA{R: 0, G: 255, B: 255, A: 255}
	YellowColor  = color.RGBA{R: 255, G: 255, B: 0, A: 255}
	OutlineColor = color.RGBA{R: 40, G: 40, B: 40, A: 255}
	BlackFill    = color.RGBA{R: 20, G: 20, B: 20, A: 255}
	WhiteFill    = color.RGBA{R: 250, G: 250, B: 250, A: 255}
	NoFill       = color.RGBA{R: 0, G: 0, B: 0, A: 0}
	FontData     = draw2d.FontData{Name: "Courier"}
	DotLocations = [][]int{{2, 2}, {6, 6}, {2, 6}, {6, 2}}
)

func init() {
	font, err := truetype.Parse(TtfFont)
	if err != nil {
		panic(fmt.Sprintf("failed to create font: %v", err))
	}
	draw2d.RegisterFont(FontData, font)
}

type RenderCache struct {
	whiteDisc  image.Image
	blackDisc  image.Image
	noDisc     image.Image
	background image.Image
}

func NewRenderCache() RenderCache {
	return RenderCache{
		whiteDisc:  DrawDisc(WhiteFill, 2.0),
		blackDisc:  DrawDisc(BlackFill, 2.0),
		noDisc:     DrawDisc(NoFill, 3.0),
		background: drawBackground(BoardSize),
	}
}

func DrawBoard(r RenderCache, board Board) image.Image {
	return DrawBoardMoves(r, board, nil)
}

func DrawBoardMoves(r RenderCache, board Board, moves []Tile) image.Image {
	img := image.NewRGBA(image.Rect(0, 0, r.background.Bounds().Dx(), r.background.Bounds().Dy()))

	DrawBoardDiscs(r, board, img)

	// draw each move image onto the preMoves
	for _, move := range moves {
		x := SideOffset + move.Col*TileSize - (LineThickness / 2)
		y := SideOffset + move.Row*TileSize - (LineThickness / 2)
		rect := image.Rect(x, y, x+r.noDisc.Bounds().Dx(), y+r.noDisc.Bounds().Dy())
		draw.Draw(img, rect, r.noDisc, image.Point{X: 0, Y: 0}, draw.Over)
	}

	return img
}

func DrawBoardAnalysis(r RenderCache, board Board, bestMoves []RankTile) image.Image {
	img := image.NewRGBA(image.Rect(0, 0, r.background.Bounds().Dx(), r.background.Bounds().Dy()))

	DrawBoardDiscs(r, board, img)

	g := draw2dimg.NewGraphicContext(img)

	// draw each heuristic eval onto the preMoves
	for i, move := range bestMoves {
		hText := fmt.Sprintf("%.1f", move.H)
		minLen := 5.0
		if move.H >= 0.0 {
			minLen = 4.0
		}
		end := int(math.Min(float64(len(hText)), minLen))
		hText = hText[0:end]

		if i == 0 {
			g.SetFillColor(CyanColor)
		} else {
			g.SetFillColor(YellowColor)
		}

		x := SideOffset + move.Col*TileSize
		y := SideOffset + move.Row*TileSize
		drawCenterString(g, AnalysisFont, hText, x, y, TileSize, TileSize)
	}

	return img
}

func DrawBoardDiscs(r RenderCache, board Board, img draw.Image) {
	draw.Draw(img, r.background.Bounds(), r.background, image.Point{X: 0, Y: 0}, draw.Over)

	// draw discs onto preMoves, either empty, black, or white
	for _, tile := range tiles {
		x := SideOffset + tile.Col*TileSize - (LineThickness / 2)
		y := SideOffset + tile.Row*TileSize - (LineThickness / 2)
		// determine which bitmap belongs in the tile slot
		disc := board.GetSquareByTile(tile)

		var discImg image.Image
		if disc == Black {
			discImg = r.blackDisc
		} else if disc == White {
			discImg = r.whiteDisc
		}

		if discImg != nil {
			rect := image.Rect(x, y, x+discImg.Bounds().Dx(), y+discImg.Bounds().Dy())
			draw.Draw(img, rect, discImg, image.Point{X: 0, Y: 0}, draw.Over)
		}
	}
}

func drawBackground(boardSize int) image.Image {
	width := TileSize*boardSize + LineThickness + SideOffset
	height := TileSize*boardSize + LineThickness + SideOffset

	img := image.NewRGBA(image.Rect(0, 0, width, height))
	g := draw2dimg.NewGraphicContext(img)

	g.SetFillColor(BlackColor)
	draw2dkit.Rectangle(g, 0, 0, float64(width), float64(height))
	g.FillStroke()

	g.SetFillColor(GreenColor)
	draw2dkit.Rectangle(g, SideOffset, SideOffset, float64(width-LineThickness), float64(height-LineThickness))
	g.FillStroke()

	g.SetLineWidth(LineThickness)
	g.SetFillColor(BlackColor)

	// draw black horizontal lines
	for i := 0; i < boardSize+1; i++ {
		y := float64(i*TileSize + SideOffset)
		g.MoveTo(SideOffset, y)
		g.LineTo(float64(width), y)
		g.Close()
		g.FillStroke()
	}

	// draw black vertical lines
	for i := 0; i < boardSize+1; i++ {
		x := float64(i*TileSize + SideOffset)
		g.MoveTo(x, SideOffset)
		g.LineTo(x, float64(height))
		g.Close()
		g.FillStroke()
	}

	g.SetFillColor(WhiteFill)

	// draw letters on horizontal sidebar
	for i := 0; i < boardSize; i++ {
		text := string(rune(i) + 'A')
		x := SideOffset + i*TileSize
		drawCenterString(g, SideFont, text, x, 0, TileSize, SideOffset)
	}

	// draw numbers on vertical sidebar
	for i := 0; i < boardSize; i++ {
		text := strconv.Itoa(i + 1)
		y := SideOffset + i*TileSize
		drawCenterString(g, SideFont, text, 0, y, SideOffset, TileSize)
	}

	g.SetFillColor(BlackFill)
	for _, location := range DotLocations {
		// fills an oval, the x,y is subtracted by the dot size and line thickness to center it
		col := location[0]
		row := location[1]
		x := SideOffset + col*TileSize
		y := SideOffset + row*TileSize

		draw2dkit.Circle(g, float64(x), float64(y), DotSize)
		g.FillStroke()
	}

	return img
}

func drawCenterString(g *draw2dimg.GraphicContext, fontSize float64, text string, x, y, width, height int) {
	g.SetFontData(FontData)
	g.SetFontSize(fontSize)

	left, top, right, bottom := g.GetStringBounds(text)
	strWidth := right - left
	strHeight := top - bottom
	// Determine the X coordinate for the text
	xDraw := float64(x) + (float64(width)-strWidth)/2
	// Determine the Y coordinate for the text (note we add the ascent, as 2d 0 is top of the screen)
	yDraw := float64(y) + ((float64(height) - strHeight) / 2)

	g.FillStringAt(text, xDraw, yDraw)
}

func DrawDisc(fillColor color.RGBA, thickness float64) image.Image {
	img := image.NewRGBA(image.Rect(0, 0, TileSize, TileSize))

	g := draw2dimg.NewGraphicContext(img)

	g.SetFillColor(fillColor)
	g.SetStrokeColor(OutlineColor)
	g.SetLineWidth(thickness)

	draw2dkit.Circle(g, float64(LineThickness/2+TileSize/2), LineThickness/2+float64(TileSize/2), TileSize/2-6)
	g.FillStroke()

	return img
}
