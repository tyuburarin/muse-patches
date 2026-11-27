/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package local.muse.extension.twitter.entity;

import local.muse.extension.twitter.entity.Debug;
import local.muse.extension.crimera.PikoUtils;

// Lcom/twitter/model/core/entity/d;
public class TweetInfo extends Debug {

    private final Object obj;

    public TweetInfo(Object obj) {
        super(obj);
        this.obj = obj;
    }

    public String getLang() throws Exception {
        // y:String
        return (String) super.getField("tweetLang");
    }

    @Override
    public String toString() {
        try {
            return "TweetInfo [getLang()=" + this.getLang() + "]";

        } catch (Exception e) {
            PikoUtils.logger(e);
            return e.getMessage();
        }

    }

}