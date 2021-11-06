/*
 * This file is part of Amproid
 *
 * Copyright (c) 2021. Peter Papp
 *
 * Please visit https://github.com/4phun/Amproid for details
 *
 * Amproid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Amproid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Amproid. If not, see http://www.gnu.org/licenses/
 */

package com.pppphun.amproid;


import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.HORIZONTAL;

import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.media.session.MediaController;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.media.MediaBrowserCompat;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pppphun.amproid.shared.Amproid;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class MediaBrowserFragment extends Fragment
{
    private static final int MEDIA_ITEM_TITLE_ID    = 101;
    private static final int MEDIA_ITEM_SUBTITLE_ID = 102;
    private static final int MEDIA_ITEM_ENTER_ID    = 103;
    private static final int MEDIA_ITEM_ICON_ID     = 104;
    private static final int MEDIA_ITEM_CLICK_PLAY  = 105;
    private static final int MEDIA_ITEM_CLICK_ENTER = 106;

    private final long imageDownloadId;


    private final Handler mediaBrowserHandler = new Handler(Looper.getMainLooper())
    {
        @Override
        public void handleMessage(@NonNull Message msg)
        {
            Bundle arguments = null;
            try {
                if (msg.obj instanceof Bundle) {
                    arguments = (Bundle) msg.obj;
                }
            }
            catch (Exception ignored) {
            }

            super.handleMessage(msg);

            try {
                if (arguments != null) {
                    if (!arguments.containsKey(getString(R.string.msg_action))) {
                        return;
                    }

                    String action = arguments.getString(getString(R.string.msg_action));
                    if (action.equals(getString(R.string.msg_action_async_finished))) {
                        int asyncType = arguments.getInt(getString(R.string.msg_async_finished_type));
                        if (asyncType == getResources().getInteger(R.integer.async_image_downloader)) {
                            String errorMessage = arguments.getString(getString(R.string.msg_error_message), "");
                            if (!errorMessage.isEmpty()) {
                                return;
                            }

                            Bitmap bitmap    = arguments.getParcelable("image");
                            String urlString = arguments.getString("url");
                            if ((bitmap == null) || (urlString == null)) {
                                return;
                            }

                            Uri uri = Uri.parse(urlString);
                            ((AmproidMainActivity) requireActivity()).getIconsCache().put(uri, bitmap);

                            View view = getView();
                            if (view == null) {
                                return;
                            }

                            RecyclerView browser = view.findViewById(R.id.browser);
                            if (browser == null) {
                                return;
                            }

                            MediaItemsAdapter adapter = (MediaItemsAdapter) browser.getAdapter();
                            if (adapter == null) {
                                return;
                            }

                            for (int i = 0; i < adapter.getItemCount(); i++) {
                                MediaItemsAdapterItem item = adapter.items[i];
                                if (uri.equals(item.getIconUri())) {
                                    final int itemPosition = i;
                                    requireActivity().runOnUiThread(new Runnable()
                                    {
                                        @Override
                                        public void run()
                                        {
                                            adapter.notifyItemChanged(itemPosition);
                                        }
                                    });
                                }
                            }
                        }
                    }
                }
            }
            catch (Exception ignored) {
                // user might navigate away from fragment
            }
        }
    };


    public MediaBrowserFragment()
    {
        super(R.layout.fragment_media_browser);
        imageDownloadId = System.currentTimeMillis();
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView browser = view.findViewById(R.id.browser);
        if (browser != null) {
            browser.setLayoutManager(new LinearLayoutManager(view.getContext()));
        }

        ImageButton refreshButton = view.findViewById(R.id.refreshButton);
        if (refreshButton != null) {
            refreshButton.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    ProgressBar loading = requireActivity().findViewById(R.id.loading);
                    if (loading != null) {
                        loading.setVisibility(View.VISIBLE);
                    }

                    setMediaItems(new ArrayList<>());

                    ((AmproidMainActivity) requireActivity()).browseFragmentRefreshPressed();
                }
            });
        }
    }


    @Override
    public void onDestroyView()
    {
        ((AmproidMainActivity) requireActivity()).downloadImageCancel(imageDownloadId);
        super.onDestroyView();
    }


    void setMediaItems(List<MediaBrowserCompat.MediaItem> children)
    {
        View view = getView();
        if (view == null) {
            return;
        }

        MediaItemsAdapterItem[] items = new MediaItemsAdapterItem[children.size()];
        for (int i = 0; i < children.size(); i++) {
            String title = getString(R.string.unknown);
            try {
                title = Objects.requireNonNull(children.get(i).getDescription().getTitle()).toString();
            }
            catch (Exception ignored) {
            }

            String subTitle = "";
            try {
                subTitle = Objects.requireNonNull(children.get(i).getDescription().getSubtitle().toString());
            }
            catch (Exception ignored) {
            }

            items[i] = new MediaItemsAdapterItem();
            items[i].setId(children.get(i).getDescription().getMediaId());
            items[i].setTitle(title);
            items[i].setSubTitle(subTitle);
            items[i].setFlags(children.get(i).getFlags());
            items[i].setIconUri(children.get(i).getDescription().getIconUri());
        }

        RecyclerView browser = view.findViewById(R.id.browser);
        if (browser != null) {
            browser.setAdapter(new MediaItemsAdapter(items));
        }
    }


    private static class MediaItemsAdapterItem
    {
        private String id;
        private String title;
        private String subTitle;
        private Uri    iconUri;
        private int    flags;


        public Uri getIconUri()
        {
            return iconUri;
        }


        public void setIconUri(Uri iconUri)
        {
            this.iconUri = iconUri;
        }


        public String getId()
        {
            return id;
        }


        public void setId(String id)
        {
            this.id = id;
        }


        public String getSubTitle()
        {
            return subTitle;
        }


        public void setSubTitle(String subTitle)
        {
            this.subTitle = subTitle;
        }


        int getFlags()
        {
            return flags;
        }


        void setFlags(int flags)
        {
            this.flags = flags;
        }


        String getTitle()
        {
            return title;
        }


        void setTitle(String title)
        {
            this.title = title;
        }
    }


    public class MediaItemsAdapter extends RecyclerView.Adapter<MediaItemsAdapter.MediaItemViewHolder>
    {
        private int backgroundColorA = 0xFFFFFFFF;
        private int backgroundColorB = 0xFFDDDDDD;

        private final MediaItemsAdapterItem[] items;


        MediaItemsAdapter(MediaItemsAdapterItem[] items)
        {
            this.items = items;

            try {
                TypedArray array = requireActivity().getTheme().obtainStyledAttributes(new int[]{android.R.attr.colorBackground, android.R.attr.colorControlHighlight});
                backgroundColorA = array.getColor(0, backgroundColorA);
                backgroundColorB = array.getColor(1, backgroundColorB);
                array.recycle();
            }
            catch (Exception ignored) {
            }
        }


        @NonNull
        @Override
        public MediaItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
        {
            FragmentActivity activity = getActivity();
            View             view     = getView();

            if ((activity == null) || (view == null)) {
                return new MediaItemViewHolder(new LinearLayout(Amproid.getAppContext()));
            }

            int iconSize         = Math.round(getResources().getDimension(R.dimen.recycler_icon_size));
            int distanceBetween  = Math.round(getResources().getDimension(R.dimen.distance_between));
            int distanceFromEdge = Math.round(getResources().getDimension(R.dimen.distance_from_edge));
            int separation       = Math.round(getResources().getDimension(R.dimen.separation));

            int screenWidth = Amproid.screenSize(requireActivity(), Amproid.ScreenSizeDimension.SCREEN_SIZE_WIDTH);
            int titleWidth  = (int) Math.round(screenWidth == 0 ? 500 : 0.85 * screenWidth - (iconSize + separation));

            boolean showIcons = false;
            for (MediaItemsAdapterItem item : items) {
                if (item.getIconUri() != null) {
                    showIcons = true;
                    break;
                }
            }

            LinearLayout.LayoutParams rowLayoutParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            rowLayoutParams.setMargins(0, 0, 0, distanceBetween);

            LinearLayout row = new LinearLayout(view.getContext());
            row.setOrientation(HORIZONTAL);
            row.setLayoutParams(rowLayoutParams);
            row.setPadding(distanceFromEdge, distanceFromEdge, distanceFromEdge, distanceFromEdge);

            if (showIcons) {
                ImageView icon = new ImageView(parent.getContext());
                icon.setId(MEDIA_ITEM_ICON_ID);
                icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
                icon.setPadding(0, 0, separation, 0);
                row.addView(icon);

                LinearLayout.LayoutParams titlesLayoutParams = new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT);

                LinearLayout titles = new LinearLayout(view.getContext());
                titles.setOrientation(LinearLayout.VERTICAL);
                titles.setLayoutParams(titlesLayoutParams);
                titles.setGravity(CENTER_VERTICAL);

                TextView title = new TextView(parent.getContext());
                title.setId(MEDIA_ITEM_TITLE_ID);
                title.setTextAppearance(R.style.TextAppearance_AppCompat_Medium);
                title.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
                title.setMaxWidth(titleWidth);
                title.setMaxLines(2);
                title.setEllipsize(TextUtils.TruncateAt.END);
                titles.addView(title);

                TextView subTitle = new TextView(parent.getContext());
                subTitle.setId(MEDIA_ITEM_SUBTITLE_ID);
                subTitle.setTextAppearance(R.style.TextAppearance_AppCompat_Small);
                subTitle.setTypeface(null, Typeface.ITALIC);
                subTitle.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
                subTitle.setMaxWidth(titleWidth);
                titles.addView(subTitle);

                row.addView(titles);
            }
            else {
                TextView title = new TextView(parent.getContext());
                title.setId(MEDIA_ITEM_TITLE_ID);
                title.setTextAppearance(R.style.TextAppearance_AppCompat_Medium);
                title.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT));
                title.setMaxWidth(titleWidth);
                title.setGravity(CENTER_VERTICAL);
                row.addView(title);
            }

            LinearLayout.LayoutParams spaceLayoutParams = new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
            spaceLayoutParams.weight = 1;

            Space space = new Space(parent.getContext());
            space.setLayoutParams(spaceLayoutParams);
            row.addView(space);

            TextView enter = new TextView(parent.getContext());
            enter.setId(MEDIA_ITEM_ENTER_ID);
            enter.setTextAppearance(R.style.TextAppearance_AppCompat_Medium);
            enter.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT));
            enter.setPadding(distanceBetween, 0, 0, 0);
            enter.setText(">>");
            enter.setGravity(CENTER_VERTICAL);
            row.addView(enter);

            return new MediaItemViewHolder(row);
        }


        @Override
        public void onBindViewHolder(@NonNull MediaItemViewHolder holder, int position)
        {

            String text = items[position].getTitle();
            if ((text == null) || text.isEmpty()) {
                text = getString(R.string.unknown);
            }

            String subText = items[position].getSubTitle();

            ImageView icon = holder.row.findViewById(MEDIA_ITEM_ICON_ID);
            if (icon != null) {
                Uri uri = items[position].getIconUri();
                if (uri == null) {
                    icon.setImageDrawable(ResourcesCompat.getDrawable(getResources(), android.R.drawable.ic_media_play, null));
                }
                else {
                    Bitmap bitmap = ((AmproidMainActivity) requireActivity()).getIconsCache().get(uri);
                    if (bitmap == null) {
                        URL url = null;
                        try {
                            url = new URL(uri.toString());
                        }
                        catch (Exception ignored) {
                        }
                        if (url != null) {
                            ((AmproidMainActivity) requireActivity()).downloadImage(url, mediaBrowserHandler, Math.round(getResources().getDimension(R.dimen.recycler_icon_size)), imageDownloadId);
                        }
                    }
                    else {
                        icon.setImageBitmap(bitmap);
                    }
                }
            }

            TextView title = holder.row.findViewById(MEDIA_ITEM_TITLE_ID);
            title.setText(text);

            TextView subTitle = holder.row.findViewById(MEDIA_ITEM_SUBTITLE_ID);
            if (subTitle != null) {
                if ((subText != null) && !subText.isEmpty()) {
                    subTitle.setText(subText);
                }
                else {
                    subTitle.setVisibility(View.GONE);
                }
            }

            TextView enter = holder.row.findViewById(MEDIA_ITEM_ENTER_ID);
            enter.setVisibility((items[position].getFlags() & MediaBrowserCompat.MediaItem.FLAG_BROWSABLE) > 0 ? View.VISIBLE : View.GONE);

            if (position % 2 == 0) {
                holder.row.setBackgroundColor(backgroundColorA);
            }
            else {
                holder.row.setBackgroundColor(backgroundColorB);
            }
        }


        @Override
        public int getItemCount()
        {
            return items.length;
        }


        public class MediaItemViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener
        {
            final LinearLayout row;


            MediaItemViewHolder(@NonNull LinearLayout itemView)
            {
                super(itemView);

                TextView title = itemView.findViewById(MEDIA_ITEM_TITLE_ID);
                title.setOnClickListener(this);

                TextView enter = itemView.findViewById(MEDIA_ITEM_ENTER_ID);
                enter.setOnClickListener(this);

                itemView.setOnClickListener(this);

                row = itemView;
            }


            @Override
            public void onClick(View v)
            {
                int position = getAdapterPosition();

                int clickedId = v.getId();
                int flags     = items[position].getFlags();

                FragmentActivity activity = getActivity();
                if (activity == null) {
                    return;
                }

                int clickAction = MEDIA_ITEM_CLICK_PLAY;
                if (((flags & MediaBrowserCompat.MediaItem.FLAG_BROWSABLE) > 0) && ((flags & MediaBrowserCompat.MediaItem.FLAG_PLAYABLE) > 0)) {
                    if (clickedId == MEDIA_ITEM_ENTER_ID) {
                        clickAction = MEDIA_ITEM_CLICK_ENTER;
                    }
                }
                else if ((flags & MediaBrowserCompat.MediaItem.FLAG_BROWSABLE) > 0) {
                    clickAction = MEDIA_ITEM_CLICK_ENTER;
                }

                if (clickAction == MEDIA_ITEM_CLICK_ENTER) {
                    ((AmproidMainActivity) activity).mediaBrowserSubscribe(items[position].getId());
                }
                else {
                    MediaController mediaController = activity.getMediaController();
                    if (mediaController != null) {
                        mediaController.getTransportControls().playFromMediaId(items[position].getId(), new Bundle());
                        ((AmproidMainActivity) activity).switchToNowPlaying();
                    }
                }
            }
        }
    }
}
