package com.ayazqureshi.dailywallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Locale;

public class FeedTest {
    private static final String JSON = "{\"images\":[{\"startdate\":\"20261110\","
            + "\"url\":\"/th?id=OHR.SampleLake_EN-IN1234567890_1920x1080.jpg&rf=LaDigue_1920x1080.jpg&pid=hp\","
            + "\"urlbase\":\"/th?id=OHR.SampleLake_EN-IN1234567890\","
            + "\"copyright\":\"A lake at dawn (\\u00a9 Some Photographer\\/Agency)\",\"title\":\"Still water\"}]}";

    @Test
    public void readsThePictureAddress() {
        assertEquals("/th?id=OHR.SampleLake_EN-IN1234567890", Feed.imageBase(JSON));
        assertEquals("https://www.bing.com/th?id=OHR.SampleLake_EN-IN1234567890_1920x1080.jpg", Feed.imageUrl(JSON));
    }

    @Test
    public void readsAnEscapedSlash() {
        assertEquals("/th?id=OHR.A_EN-US1", Feed.imageBase("{\"urlbase\":\"\\/th?id=OHR.A_EN-US1\"}"));
    }

    @Test
    public void refusesABaseThatPointsElsewhere() {
        assertNull(Feed.imageBase("{\"urlbase\":\"//example.com/x\"}"));
        assertNull(Feed.imageBase("{\"urlbase\":\"/th?id=OHR.A&redirect=http://example.com\"}"));
        assertNull(Feed.imageBase("{\"images\":[]}"));
        assertNull(Feed.imageBase("<html>captive portal</html>"));
        assertNull(Feed.imageBase(null));
    }

    @Test
    public void readsTheCaption() {
        assertEquals("A lake at dawn (© Some Photographer/Agency)", Feed.caption(JSON));
        assertEquals("", Feed.caption("{}"));
    }

    @Test
    public void marketFollowsTheLocale() {
        assertEquals("en-IN", Feed.market(new Locale("en", "IN")));
        assertEquals("de-DE", Feed.market(Locale.GERMANY));
        assertEquals("en-US", Feed.market(new Locale("en")));
        assertEquals("en-US", Feed.market(null));
        assertEquals("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=en-IN", Feed.url("en-IN"));
    }
}
