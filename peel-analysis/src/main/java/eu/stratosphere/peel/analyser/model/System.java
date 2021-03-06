package eu.stratosphere.peel.analyser.model;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * The system the experiment was executed on
 * Created by Fabian on 15.10.14.
 */
@Entity
public class System {

    private String Name;
    private String Version;
    private Integer SystemID;
    private Set<Experiment> experimentSet;

    public System() {
        experimentSet = new HashSet<>();
    }

    @Id
    @GeneratedValue
    public Integer getSystemID() {
        return SystemID;
    }

    public void setSystemID(Integer ID) {
        this.SystemID = ID;
    }

    @Column
    public String getName() {
        return Name;
    }

    public void setName(String name) {
        Name = name;
    }

    @Column
    public String getVersion() {
        return Version;
    }

    public void setVersion(String version) {
        Version = version;
    }

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinColumn
    public Set<Experiment> getExperimentSet() {
        return experimentSet;
    }

    public void setExperimentSet(Set<Experiment> experimentSet) {
        this.experimentSet = experimentSet;
    }
}
