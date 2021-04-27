package me.synology.mmyu.umastat;

public class ScriptAndSpecs {
    private int id;
    private String script;
    private String spec;
    private int iter;
    private int iter2;
    private String char_name;

    public ScriptAndSpecs(){}

    public ScriptAndSpecs(int id, String script, String spec, int iter, int iter2, String char_name){
        this.id = id;
        this.script = script;
        this.spec = spec;
        this.iter = iter;
        this.iter2 = iter2;
        this.char_name = char_name;
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

    public String getChar_name() {
        return char_name;
    }

    public void setChar_name(String char_name) {
        this.char_name = char_name;
    }
}
