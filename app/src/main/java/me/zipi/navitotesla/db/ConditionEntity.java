package me.zipi.navitotesla.db;

import java.util.Date;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(tableName = "condition", indices = @Index(value = {"type", "name"}, unique = true))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(onConstructor = @__({@Ignore}))
public class ConditionEntity {

    @PrimaryKey(autoGenerate = true)
    private Integer id;

    private String name;
    // wifi bluetooth
    private String type;

    private Date created;

}
