package com.felixmulder.dotty.plugin;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.io.File;

public class IDEConfig {
  public String id;
  public File[] sources;
  public String[] scalacArgs;
  public File[] depCp;
  public File target;

  // Constructor required by Jackson
  public IDEConfig() {
  }

  public IDEConfig(String id, File[] sources, String[] scalacArgs, File[] depCp, File target) {
     this.id = id;
     this.sources = sources;
     this.scalacArgs = scalacArgs;
     this.depCp = depCp;
     this.target =target;
  }
}
