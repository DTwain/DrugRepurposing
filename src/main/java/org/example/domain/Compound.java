package org.example.domain;

import com.google.gson.annotations.SerializedName;

public class Compound extends Entity<String>{
    @SerializedName("description")
    private String eliases;
    private String name;

    public Compound(String id, String eliases) {
        setId(id);
        this.eliases = eliases;
    }

    public Compound(String name) {
        this.name = name;
    }


    public String getEliases() {
        return eliases;
    }

    public void setEliases(String eliases) {
        this.eliases = eliases;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
