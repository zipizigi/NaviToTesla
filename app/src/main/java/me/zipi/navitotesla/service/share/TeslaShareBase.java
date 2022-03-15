package me.zipi.navitotesla.service.share;

import android.content.Context;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class TeslaShareBase implements TeslaShare {
    protected final Context context;
}
