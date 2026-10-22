package com.kiminonawa.mydiary.entries;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.util.SparseArray;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.db.DBManager;
import com.kiminonawa.mydiary.entries.calendar.CalendarFragment;
import com.kiminonawa.mydiary.entries.diary.DiaryFragment;
import com.kiminonawa.mydiary.db.DBStructure;
import com.kiminonawa.mydiary.entries.entries.EntriesEntity;
import com.kiminonawa.mydiary.entries.entries.EntriesFragment;
import com.kiminonawa.mydiary.shared.BackgroundTask;
import com.kiminonawa.mydiary.shared.ThemeManager;
import com.kiminonawa.mydiary.shared.statusbar.ChinaPhoneHelper;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.hoang8f.android.segmented.SegmentedGroup;

public class DiaryActivity extends FragmentActivity implements RadioGroup.OnCheckedChangeListener {


    /**
     * Public data
     */
    private long topicId;
    private boolean hasEntries;

    /**
     * UI
     */
    private LinearLayout LL_diary_topbar_content;
    private ViewPager ViewPager_diary_content;
    private TextView TV_diary_topbar_title;
    private SegmentedGroup SG_diary_topbar;
    private RadioButton But_diary_topbar_entries, But_diary_topbar_calendar, But_diary_topbar_diary;

    /**
     * View pager
     */
    private ScreenSlidePagerAdapter mPagerAdapter;

    /**
     * Google API
     */

    /**
     * The diary list for every fragment
     */
    private List<EntriesEntity> entriesList = new ArrayList<>();
    //To ignore the answer of a reading when a newer one was asked
    private int entriesLoadSequence = 0;
    private final static int MAX_TEXT_LENGTH = 18;

    /**
     * Optional: open this diary right after the page is shown (used by "a year ago you wrote this")
     */
    public final static String EXTRA_OPEN_DIARY_ID = "open_diary_id";
    private long openDiaryId = -1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diary);
        //For set status bar
        ChinaPhoneHelper.setStatusBar(this, true);

        topicId = getIntent().getLongExtra("topicId", -1);
        hasEntries = getIntent().getBooleanExtra("has_entries", true);
        openDiaryId = getIntent().getLongExtra(EXTRA_OPEN_DIARY_ID, -1);
        if (topicId == -1) {
            finish();
        }
        /**
         * init UI
         */
        LL_diary_topbar_content = (LinearLayout) findViewById(R.id.LL_diary_topbar_content);
        SG_diary_topbar = (SegmentedGroup) findViewById(R.id.SG_diary_topbar);
        SG_diary_topbar.setOnCheckedChangeListener(this);
        SG_diary_topbar.setTintColor(ThemeManager.getInstance().getThemeDarkColor(this));
        But_diary_topbar_entries = (RadioButton) findViewById(R.id.But_diary_topbar_entries);
        But_diary_topbar_calendar = (RadioButton) findViewById(R.id.But_diary_topbar_calendar);
        But_diary_topbar_diary = (RadioButton) findViewById(R.id.But_diary_topbar_diary);
        TV_diary_topbar_title = (TextView) findViewById(R.id.TV_diary_topbar_title);
        TV_diary_topbar_title.setTextColor(ThemeManager.getInstance().getThemeDarkColor(this));

        String diaryTitle = getIntent().getStringExtra("diaryTitle");
        if (diaryTitle == null) {
            diaryTitle = "Diary";
        }
        TV_diary_topbar_title.setText(diaryTitle);
        //After SegmentedGroup was created
        initViewPager();

        //Load the all entries from db.
        loadEntries();
    }


    /**
     * Reads all the entries of the diary from the database in the background (a big diary needs some time) and
     * then tells the pages to draw them again.
     */
    public void loadEntries() {
        final int sequence = ++entriesLoadSequence;
        final long topic = getTopicId();
        final String noTitle = getString(R.string.diary_no_title);
        final DiaryActivity activity = this;
        new BackgroundTask<Void, List<EntriesEntity>>() {
            @Override
            protected List<EntriesEntity> doInBackground(Void... params) {
                return readEntries(activity, topic, noTitle);
            }

            @Override
            protected void onPostExecute(List<EntriesEntity> entries) {
                //A newer reading is already on its way: this one is old
                if (isFinishing() || sequence != entriesLoadSequence) {
                    return;
                }
                entriesList.clear();
                entriesList.addAll(entries);
                notifyEntriesLoaded();
            }
        }.execute();
    }

    private static List<EntriesEntity> readEntries(Context context, long topicId, String noTitle) {
        List<EntriesEntity> entries = new ArrayList<>();
        DBManager dbManager = new DBManager(context);
        dbManager.opeDB();
        try {
            //Select diary info
            Cursor diaryCursor = dbManager.selectDiaryList(topicId);
            int tagsColumn = diaryCursor.getColumnIndex(DBStructure.DiaryEntry_V2.COLUMN_TAGS);
            int thumbnailColumn = diaryCursor.getColumnIndex(DBStructure.DiaryEntry_V2.COLUMN_THUMBNAIL);
            for (int i = 0; i < diaryCursor.getCount(); i++) {
                String title = diaryCursor.getString(2);
                if ("".equals(title)) {
                    title = noTitle;
                }
                EntriesEntity entity = new EntriesEntity(diaryCursor.getLong(0),
                        new Date(diaryCursor.getLong(1)),
                        //The whole title: the list cuts it with "..." when it is too long
                        title,
                        diaryCursor.getInt(4), diaryCursor.getInt(3),
                        diaryCursor.getInt(5) > 0);
                if (tagsColumn >= 0) {
                    entity.setTags(diaryCursor.getString(tagsColumn));
                }
                //The small picture (the one chosen as the cover, or else the first photo) and the first words
                String chosenThumbnail = thumbnailColumn >= 0 ? diaryCursor.getString(thumbnailColumn) : null;
                EntryPreview preview = EntryPreview.read(context, dbManager, topicId, entity.getId(), chosenThumbnail);
                if (preview.hasItems) {
                    entity.setThumbnailPath(preview.thumbnailPath);
                    entity.setThumbnailArea(preview.thumbnailArea);
                    entity.setSummary(preview.listSummary(context, MAX_TEXT_LENGTH));
                }
                entries.add(entity);
                diaryCursor.moveToNext();
            }
            diaryCursor.close();
        } finally {
            dbManager.closeDB();
        }
        return entries;
    }

    /**
     * The entries were read: the list and the calendar draw them again (a page that is not open yet reads them
     * when it opens).
     */
    private void notifyEntriesLoaded() {
        if (mPagerAdapter == null) {
            return;
        }
        Fragment entriesFragment = mPagerAdapter.getRegisteredFragment(0);
        if (entriesFragment instanceof EntriesFragment) {
            ((EntriesFragment) entriesFragment).onEntriesLoaded();
        }
        Fragment calendarFragment = mPagerAdapter.getRegisteredFragment(1);
        if (calendarFragment instanceof CalendarFragment) {
            ((CalendarFragment) calendarFragment).refreshCalendar();
        }
    }

    public List<EntriesEntity> getEntriesList() {
        return entriesList;
    }

    /**
     * Init Viewpager
     */
    private void initViewPager() {
        ViewPager_diary_content = (ViewPager) findViewById(R.id.ViewPager_diary_content);
        //Make viewpager load one fragment every time.
        ViewPager_diary_content.setOffscreenPageLimit(2);
        mPagerAdapter = new ScreenSlidePagerAdapter(getSupportFragmentManager());
        ViewPager_diary_content.setAdapter(mPagerAdapter);
        ViewPager_diary_content.addOnPageChangeListener(onPageChangeListener);
        ViewPager_diary_content.setBackground(
                ThemeManager.getInstance().getEntriesBgDrawable(this, getTopicId()));
        if (!hasEntries) {
            ViewPager_diary_content.setCurrentItem(2);
            //Set Default Checked Item
            But_diary_topbar_diary.setChecked(true);
        } else {
            //Set Default Checked Item
            But_diary_topbar_entries.setChecked(true);
        }
    }



    public long getTopicId() {
        return topicId;
    }

    /**
     * The diary that has to be opened when the entries page is ready (-1 = none). It is given only once.
     */
    public long consumeOpenDiaryId() {
        long id = openDiaryId;
        openDiaryId = -1;
        return id;
    }

    public void gotoPage(int position) {
        ViewPager_diary_content.setCurrentItem(position);
    }

    public void callEntriesGotoDiaryPosition(int position) {
        EntriesFragment entriesFragment = ((EntriesFragment) mPagerAdapter.getRegisteredFragment(0));
        if (entriesFragment != null) {
            gotoPage(0);
            entriesFragment.gotoDiaryPosition(position);
        }
    }

    public void callEntriesListRefresh() {

        EntriesFragment entriesFragment = ((EntriesFragment) mPagerAdapter.getRegisteredFragment(0));
        if (entriesFragment != null) {
            entriesFragment.updateEntriesData();
        }
    }

    public void callCalendarRefresh() {
        CalendarFragment calendarFragment = ((CalendarFragment) mPagerAdapter.getRegisteredFragment(1));
        if (calendarFragment != null) {
            calendarFragment.refreshCalendar();
        }

    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        switch (checkedId) {
            case R.id.But_diary_topbar_entries:
                ViewPager_diary_content.setCurrentItem(0);
                break;
            case R.id.But_diary_topbar_calendar:
                ViewPager_diary_content.setCurrentItem(1);
                break;
            case R.id.But_diary_topbar_diary:
                ViewPager_diary_content.setCurrentItem(2);
                break;
        }
    }


    private ViewPager.OnPageChangeListener onPageChangeListener = new ViewPager.SimpleOnPageChangeListener() {
        @Override
        public void onPageSelected(int position) {
            switch (position) {
                default:
                    But_diary_topbar_entries.setChecked(true);
                    break;
                case 1:
                    But_diary_topbar_calendar.setChecked(true);
                    break;
                case 2:
                    But_diary_topbar_diary.setChecked(true);
                    break;
            }
        }
    };


    private class ScreenSlidePagerAdapter extends FragmentPagerAdapter {

        private SparseArray<Fragment> registeredFragments = new SparseArray<Fragment>();

        public ScreenSlidePagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public BaseDiaryFragment getItem(int position) {
            BaseDiaryFragment fragment;
            switch (position) {
                default:
                    fragment = new EntriesFragment();
                    break;
                case 1:
                    fragment = new CalendarFragment();
                    break;
                case 2:
                    fragment = new DiaryFragment();
                    break;
            }
            return fragment;
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            registeredFragments.put(position, fragment);
            return fragment;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            registeredFragments.remove(position);
            super.destroyItem(container, position, object);
        }

        public Fragment getRegisteredFragment(int position) {
            return registeredFragments.get(position);
        }

    }



}
