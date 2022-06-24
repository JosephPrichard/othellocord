package bot.builders.string;

import static bot.utils.StringUtils.column;

public class TableStringBuilder
{
    int[] padding;
    private final StringBuilder table = new StringBuilder();

    public TableStringBuilder(int... padding) {
        this.padding = padding;
    }

    public void addHeaders(String... headers) {
        addBorder();
        addRow(headers);
    }

    public void addRow(String... elements) {
        table.append("| ");
        for (int i = 0; i < padding.length; i++) {
            table.append(column(elements[i], padding[i], " | "));
        }
        table.append("\n");
        addBorder();
    }

    public void addBorder() {
        table.append("+-");
        for (int j : padding) {
            table.append("-".repeat(j)).append("-+-");
        }
        table.deleteCharAt(table.length() - 1);
        table.append("\n");
    }

    @Override
    public String toString() {
        return table.toString();
    }
}
