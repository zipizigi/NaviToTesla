package me.zipi.navitotesla.db;

import java.util.Calendar;
import java.util.Date;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(tableName = "poi_address", indices = @Index(value = "poi", unique = true))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(onConstructor = @__({@Ignore}))
public class PoiAddressEntity {
    public static Integer expireDay = 10;

    @PrimaryKey(autoGenerate = true)
    private Integer id;

    private String poi;
    private String address;
    @Getter(AccessLevel.NONE)
    private Boolean registered;
    private Date created;

    public boolean isExpire() {
        long diff = created.getTime() - Calendar.getInstance().getTime().getTime();
        return registered != null && !registered && Math.abs(diff) / 1000L / 60L / 60L / 24L >= expireDay;
    }


    public Boolean isRegistered() {
        return registered != null && registered;
    }

}
