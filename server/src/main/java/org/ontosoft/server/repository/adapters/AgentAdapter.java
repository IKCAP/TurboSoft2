package org.ontosoft.server.repository.adapters;

import edu.isi.kcap.ontapi.KBAPI;

public class AgentAdapter extends EnumerationEntityAdapter {

  public AgentAdapter(KBAPI kb, KBAPI ontkb, KBAPI enumkb, String clsid) {
    super(kb, ontkb, enumkb, clsid);
  }

}
