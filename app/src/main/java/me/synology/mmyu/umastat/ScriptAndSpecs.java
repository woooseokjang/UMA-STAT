package me.synology.mmyu.umastat;

public class ScriptAndSpecs {
    private int id;
    private String script;
    private String spec;
    private int iter;
    private int iter2;

    public ScriptAndSpecs(){}

    public ScriptAndSpecs(int id, String script, String spec, int iter, int iter2){
        this.id = id;
        this.script = script;
        this.spec = spec;
        this.iter = iter;
        this.iter2 = iter2;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public void setIter(int iter) {
        this.iter = iter;
    }

    public void setIter2(int iter2) {
        this.iter2 = iter2;
    }

    public int getId() {
        return id;
    }

    public String getScript() {
        return script;
    }

    public String getSpec() {
        return spec;
    }

    public int getIter() {
        return iter;
    }

    public int getIter2() {
        return iter2;
    }
}
