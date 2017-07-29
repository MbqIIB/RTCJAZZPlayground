package com.ibm.team.filesystem.cli.client.internal.createcommand;

import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;








public abstract class CreateWorkspaceBaseOptions
  implements IOptionSource
{
  public CreateWorkspaceBaseOptions() {}
  
  public static final IOptionKey OPT_NAME = new OptionKey("name", "@");
  public static final IOptionKey OPT_STREAM = new OptionKey("stream", "@");
  public static final IOptionKey OPT_SNAPSHOT = new OptionKey("snapshot", "@");
  public static final NamedOptionDefinition OPT_DESC = new NamedOptionDefinition("d", "description", 1);
  public static final IOptionKey OPT_DUPLICATE = new OptionKey("duplicate", "@");
  
  public MutuallyExclusiveGroup getMutuallyExclusiveOptions() {
    return 
    
      new MutuallyExclusiveGroup().addOption(new NamedOptionDefinition(OPT_STREAM, "s", "stream", 1, "@"), getFlowTargetHelp(), false).addOption(new NamedOptionDefinition(OPT_SNAPSHOT, null, "snapshot", 1, "@"), getSnapshotHelp(), false);
  }
  
  public String getNameHelp() {
    return "";
  }
  
  public String getFlowTargetHelp() {
    return "";
  }
  
  public String getSnapshotHelp() {
    return "";
  }
  
  public String getDuplicateHelp() {
    return "";
  }
}
