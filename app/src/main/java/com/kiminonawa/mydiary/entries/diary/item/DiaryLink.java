package com.kiminonawa.mydiary.entries.diary.item;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.drawee.view.SimpleDraweeView;
import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.shared.LinkPreview;
import com.kiminonawa.mydiary.shared.ScreenHelper;
import com.kiminonawa.mydiary.shared.ThemeManager;

/**
 * A link inside a diary, shown as a card with the picture, the title and the description of the web page.
 * Only the link is saved in the entry: the preview is read from the page (and kept in the temporary memory of the app),
 * so without internet the card shows just the link. Touch the card to open the page.
 */
public class DiaryLink implements IDairyRow {

    private final Context context;
    private final View root;
    private final SimpleDraweeView SDV_image;
    private final TextView TV_title, TV_description, TV_domain;
    private final ImageView IV_delete;

    private String link = "";
    private int position;
    //Which link the preview that is coming is for (the content can change while it is loading)
    private int loadToken = 0;

    public DiaryLink(Context context) {
        this.context = context;
        root = LayoutInflater.from(context).inflate(R.layout.layout_diary_link, null, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int margin = ScreenHelper.dpToPixel(context.getResources(), 6);
        params.topMargin = margin;
        params.bottomMargin = margin;
        root.setLayoutParams(params);
        SDV_image = (SimpleDraweeView) root.findViewById(R.id.SDV_diary_link_image);
        TV_title = (TextView) root.findViewById(R.id.TV_diary_link_title);
        TV_description = (TextView) root.findViewById(R.id.TV_diary_link_description);
        TV_domain = (TextView) root.findViewById(R.id.TV_diary_link_domain);
        IV_delete = (ImageView) root.findViewById(R.id.IV_diary_link_delete);
        TV_domain.setTextColor(ThemeManager.getInstance().getThemeMainColor(context));
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPage();
            }
        });
        setEditMode(false);
    }

    /**
     * Edit mode: the delete button.
     */
    public void setDeleteClickListener(View.OnClickListener listener) {
        IV_delete.setOnClickListener(listener);
    }

    private void openPage() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, R.string.link_error_open, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void setContent(String content) {
        String normalized = LinkPreview.normalize(content);
        link = normalized != null ? normalized : (content == null ? "" : content.trim());
        final int token = ++loadToken;
        //Until the page answers (or when it does not): the link itself
        TV_title.setText(link);
        TV_description.setVisibility(View.GONE);
        TV_domain.setText(LinkPreview.domainOf(link));
        SDV_image.setVisibility(View.GONE);
        if (normalized == null) {
            return;
        }
        LinkPreview.load(context, normalized, new LinkPreview.Callback() {
            @Override
            public void onPreview(LinkPreview preview) {
                if (token != loadToken) {
                    return;
                }
                if (preview.title.length() > 0) {
                    TV_title.setText(preview.title);
                }
                if (preview.description.length() > 0) {
                    TV_description.setText(preview.description);
                    TV_description.setVisibility(View.VISIBLE);
                }
                String domain = LinkPreview.domainOf(link);
                if (preview.siteName.length() > 0 && !preview.siteName.equalsIgnoreCase(domain)) {
                    domain = preview.siteName + " · " + domain;
                }
                TV_domain.setText(domain);
                if (preview.imageFile != null) {
                    SDV_image.setImageURI(Uri.fromFile(preview.imageFile));
                    SDV_image.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    @Override
    public String getContent() {
        return link;
    }

    @Override
    public int getType() {
        return TYPE_LINK;
    }

    @Override
    public View getView() {
        return root;
    }

    @Override
    public void setEditMode(boolean isEditMode) {
        IV_delete.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
    }

    @Override
    public void setPosition(int position) {
        this.position = position;
        IV_delete.setTag(position);
    }

    @Override
    public int getPosition() {
        return position;
    }
}
