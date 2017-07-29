package com.ibm.team.filesystem.cli.client.internal.lock;

import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IVersionableHandle;
import com.ibm.team.scm.common.IWorkspace;














public class LockEntry
  implements Comparable<LockEntry>
{
  private String nameSource;
  private UUID versionableUuid;
  private IVersionableHandle versionable;
  private String versionableName;
  private IContributor owner;
  private IComponent component;
  private IWorkspace stream;
  
  public LockEntry(IWorkspace stream, IComponent component, UUID versionableUuid, IContributor owner) {}
  
  public LockEntry(IWorkspace stream, IComponent component, IVersionableHandle versionable, String nameSource, String versionableName, IContributor owner)
  {
    this.nameSource = nameSource;
    this.versionable = versionable;
    versionableUuid = versionable.getItemId();
    this.versionableName = versionableName;
    this.owner = owner;
    this.component = component;
    this.stream = stream;
  }
  
  public String getNameSource() { return nameSource; }
  
  public IVersionableHandle getVersionable() {
    return versionable;
  }
  
  public UUID getVersionableUUID() { return versionableUuid; }
  
  public String getVersionableName() {
    return versionableName;
  }
  
  public IContributor getOwner() { return owner; }
  
  public IComponent getComponent() {
    return component;
  }
  
  public IWorkspace getStream() { return stream; }
  
  public int compareTo(LockEntry o) {
    int compare = 0;
    if (getOwner() == null) {
      if (o.getOwner() == null) {
        compare = 0;
      } else {
        compare = 1;
      }
    }
    else if (o.getOwner() == null) {
      compare = -1;
    } else {
      compare = getOwner().getName().compareToIgnoreCase(o.getOwner().getName());
    }
    
    if (compare == 0) {
      compare = getStream().getName().compareToIgnoreCase(o.getStream().getName());
    }
    if (compare == 0) {
      compare = getComponent().getName().compareToIgnoreCase(o.getComponent().getName());
    }
    if (compare == 0) {
      compare = getVersionableName().compareToIgnoreCase(o.getVersionableName());
    }
    return compare;
  }
}
