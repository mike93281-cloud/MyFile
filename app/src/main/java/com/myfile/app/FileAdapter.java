package com.myfile.app;

import android.content.Context;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Adaptador común para la vista de lista y la de cuadrícula. */
public final class FileAdapter extends BaseAdapter {
    private final Context ctx;
    private final LayoutInflater inflater;
    private final ThumbLoader thumbs;
    private final boolean grid;
    private final List<FileItem> items = new ArrayList<>();
    private final Set<String> selected;
    private final int selectedColor;
    private final int thumbPx;

    public FileAdapter(Context ctx, ThumbLoader thumbs, boolean grid, Set<String> selected) {
        this.ctx = ctx;
        this.inflater = LayoutInflater.from(ctx);
        this.thumbs = thumbs;
        this.grid = grid;
        this.selected = selected;
        this.selectedColor = ctx.getColor(R.color.selected);
        this.thumbPx = (int) (ctx.getResources().getDisplayMetrics().density * (grid ? 96 : 48));
    }

    public void setItems(List<FileItem> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    public List<FileItem> items() { return items; }

    @Override public int getCount() { return items.size(); }
    @Override public FileItem getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder h;
        if (convertView == null) {
            convertView = inflater.inflate(grid ? R.layout.item_file_grid : R.layout.item_file_list, parent, false);
            h = new Holder();
            h.icon = convertView.findViewById(R.id.icon);
            h.check = convertView.findViewById(R.id.check);
            h.name = convertView.findViewById(R.id.name);
            h.meta = convertView.findViewById(R.id.meta);
            convertView.setTag(h);
        } else {
            h = (Holder) convertView.getTag();
        }
        FileItem f = items.get(position);
        h.name.setText(f.name);
        if (h.meta != null) h.meta.setText(meta(f));

        h.icon.setTag(null);
        h.icon.setImageResource(FileTypes.iconFor(f));
        if (FileTypes.wantsThumb(f)) thumbs.load(h.icon, f, thumbPx);

        boolean isSel = selected.contains(f.uri.toString());
        h.check.setVisibility(isSel ? View.VISIBLE : View.GONE);
        convertView.setBackgroundColor(isSel ? selectedColor : 0);
        return convertView;
    }

    private String meta(FileItem f) {
        StringBuilder sb = new StringBuilder();
        if (f.modified > 0) {
            Date d = new Date(f.modified);
            sb.append(DateFormat.getMediumDateFormat(ctx).format(d))
              .append(' ')
              .append(DateFormat.getTimeFormat(ctx).format(d));
        }
        if (!f.isDir() && f.size >= 0) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(Formatter.formatShortFileSize(ctx, f.size));
        }
        return sb.toString();
    }

    private static final class Holder {
        ImageView icon;
        ImageView check;
        TextView name;
        TextView meta;
    }

    public static Set<String> newSelection() {
        return new HashSet<>();
    }
}
