package org.ontosoft.client.application.publish;

import java.util.List;

import org.gwtbootstrap3.client.ui.AnchorListItem;
import org.gwtbootstrap3.client.ui.Breadcrumbs;
import org.gwtbootstrap3.client.ui.Button;
import org.gwtbootstrap3.client.ui.ButtonGroup;
import org.gwtbootstrap3.client.ui.Column;
import org.gwtbootstrap3.client.ui.Heading;
import org.ontosoft.client.application.ParameterizedViewImpl;
import org.ontosoft.client.authentication.SessionStorage;
import org.ontosoft.client.components.chart.CategoryBarChart;
import org.ontosoft.client.components.chart.CategoryPieChart;
import org.ontosoft.client.components.chart.events.CategorySelectionEvent;
import org.ontosoft.client.components.form.SoftwareForm;
import org.ontosoft.client.components.form.events.PluginResponseEvent;
import org.ontosoft.client.components.form.events.SoftwareChangeEvent;
import org.ontosoft.client.components.form.notification.PluginNotifications;
import org.ontosoft.client.place.NameTokens;
import org.ontosoft.client.rest.AppNotification;
import org.ontosoft.client.rest.SoftwareREST;
import org.ontosoft.client.rest.UserREST;
import org.ontosoft.shared.classes.entities.Software;
import org.ontosoft.shared.classes.permission.AccessMode;
import org.ontosoft.shared.classes.permission.Authorization;
import org.ontosoft.shared.classes.users.UserCredentials;
import org.ontosoft.shared.classes.users.UserSession;
import org.ontosoft.shared.classes.util.KBConstants;
import org.ontosoft.shared.classes.vocabulary.MetadataCategory;
import org.ontosoft.shared.classes.vocabulary.Vocabulary;
import org.ontosoft.shared.plugins.PluginResponse;

import com.github.gwtd3.api.D3;
import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.ListBox;
import org.gwtbootstrap3.client.ui.Modal;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class PublishView extends ParameterizedViewImpl 
  implements PublishPresenter.MyView {

  @UiField
  CategoryPieChart piechart;
  
  @UiField
  CategoryBarChart barchart;
  
  @UiField
  Column sidecolumn, piecolumn, barcolumn;
  
  @UiField
  Breadcrumbs breadcrumbs;

  @UiField
  SoftwareForm softwareform;
  
  @UiField
  ButtonGroup buttons;
  
  @UiField
  Button savebutton, reloadbutton, permbutton;
  
  @UiField
  Button setpermbutton;
  
  @UiField
  VerticalPanel loading;
  
  @UiField
  Heading heading;
  
  @UiField
  PluginNotifications notifications;
  
  @UiField
  ListBox userlist, permlist;
  
  @UiField
  Modal permissiondialog;
  
  Vocabulary vocabulary;
  String softwarename;
  Software software;
  String loggedinuser;
  
  interface Binder extends UiBinder<Widget, PublishView> { }

  @Inject
  public PublishView(Binder binder) {
    initWidget(binder.createAndBindUi(this));
    initVocabulary();
  }
  
  // If some parameters are passed in, initialize the software and interface
  public void initializeParameters(String[] params) {
    clear();
    UserSession session = SessionStorage.getSession();
    if(session == null) {
      loading.setVisible(false);
      //SoftwareREST.notifyFailure("You need to be logged in to edit software description");
      return;
    }
    
    if(session.getUsername() != null)
      this.loggedinuser = session.getUsername();
    
    // Parse tokens
    if(params.length > 0) {
      this.softwarename = params[0];
      String pfx = KBConstants.CATNS();
      String piecat = params.length > 1 ? pfx+params[1] : null;
      String barcat = params.length > 2 ? pfx+params[2] : null;

      piechart.setActiveCategoryId(piecat, false);
      barchart.setActiveCategoryId(barcat, false);

      initSoftware(this.softwarename);
    }
    else {
      software = null; 
    }
  }
  
  private void clear() {
    //notifications.setVisible(false);
    breadcrumbs.setVisible(false);
    heading.setVisible(false);
    piechart.setActiveCategoryId(null, false);
    barchart.setActiveCategoryId(null, false);
    softwareform.setVisible(false);
    piechart.setVisible(false);
    buttons.setVisible(false);
    barchart.setVisible(false);
    breadcrumbs.clear();  
    permbutton.setVisible(false);
  }
  
  private void initVocabulary() {
    SoftwareREST.getVocabulary(new Callback<Vocabulary, Throwable>() {
      @Override
      public void onSuccess(Vocabulary vocab) {
        vocabulary = vocab;
        initialDraw();
      }
      @Override
      public void onFailure(Throwable reason) { }
    }, false);
  }
  
  private void initSoftware(String softwarename) {
    initSoftware(softwarename, false);
  }
  
  private void initSoftware(String softwarename, final boolean reload) {    
    if(!reload)
      loading.setVisible(true);
    else
      reloadbutton.setIconSpin(true);
    
    SoftwareREST.getSoftware(softwarename, 
        new Callback<Software, Throwable>() {
      @Override
      public void onSuccess(Software sw) {
        reloadbutton.setIconSpin(false);
        loading.setVisible(false);
        savebutton.setEnabled(sw.isDirty());
        UserSession session = SessionStorage.getSession();
        
        if ((session != null && session.getRoles().contains("admin")) || 
          loggedinuser.equals(sw.getPermission().getOwner().getName())) {
          permbutton.setVisible(true);
        }
        
        software = sw;
        initialDraw();
        
        notifications.showNotificationsForSoftware(software.getId());
        
        initMaterial();
        Window.scrollTo(0, 0);
      }
      @Override
      public void onFailure(Throwable reason) {
        reloadbutton.setIconSpin(false);
        loading.setVisible(false);
      }
    }, reload);
  }
  
  private void initialDraw() {
    if(this.vocabulary == null || this.software == null)
      return;

    piechart.setVocabulary(vocabulary);
    barchart.setVocabulary(vocabulary);
    softwareform.setVocabulary(vocabulary);
    
    piechart.setSoftware(software);
    barchart.setSoftware(software);
    softwareform.setSoftware(software);
    
    softwareform.createFormItems();
    initializePiechart();

    setBreadCrumbs();
    heading.setVisible(true);
    
    if(piechart.getActiveCategoryId() != null) {
      MetadataCategory mcat = 
          piechart.getVocabulary().getCategory(piechart.getActiveCategoryId());
      if(mcat != null) {
        piechart.setActiveCategoryId(mcat.getId(), false);
        pieCategorySelected(mcat.getId());
      }
    }
    else
      piechart.setActiveCategoryId(null, false);
    
    if(barchart.getActiveCategoryId() != null) {
      MetadataCategory mcat = 
          barchart.getVocabulary().getCategory(barchart.getActiveCategoryId());
      if(mcat != null) {
        barchart.setActiveCategoryId(mcat.getId(), false);
        barCategorySelected(barchart.getActiveCategoryId());
      }
    }
  }

  private void initializePiechart() {
    if(!piechart.drawnCategories())
      piechart.drawCategories();
    
    piechart.fillCategories(true);

    sidecolumn.setSize("XS_12");
    piecolumn.setSize("XS_10, SM_8, MD_6");
    piecolumn.setOffset("XS_1, SM_2, MD_3");
    
    easeIn(piechart);
    piechart.setVisible(true);
    buttons.setVisible(true);
    
    piechart.updateDimensions();
  }
  
  @UiHandler("piechart")
  void onPieSelect(CategorySelectionEvent event) {
    MetadataCategory pcat = vocabulary.getCategory(piechart.getActiveCategoryId());
    if(pcat != null) {
      History.replaceItem(NameTokens.publish + "/" + softwarename + "/" + pcat.getName(), false);
      barchart.setActiveCategoryId(null, false);
      pieCategorySelected(pcat.getId());
      setBreadCrumbs();
    }
  }
  
  @UiHandler("barchart")
  void onBarSelect(CategorySelectionEvent event) {
    MetadataCategory pcat = vocabulary.getCategory(piechart.getActiveCategoryId());
    MetadataCategory bcat = vocabulary.getCategory(barchart.getActiveCategoryId());
    if(bcat != null && pcat != null) {
      History.replaceItem(NameTokens.publish + "/" + softwarename + "/"
            + pcat.getName() + "/" + bcat.getName() , false);
      barCategorySelected(bcat.getId());
    }
    setBreadCrumbs();
  }

  @UiHandler("savebutton")
  public void onSave(ClickEvent event) {
    final Software tmpsw = softwareform.getSoftware();
    tmpsw.setName(softwarename);
    //savebutton.state().loading();
    SoftwareREST.updateSoftware(tmpsw, new Callback<Software, Throwable>() {
      @Override
      public void onSuccess(Software sw) {
        software = sw;
        softwarename = tmpsw.getName();
        piechart.setSoftware(software);
        barchart.setSoftware(software);
        softwareform.setSoftware(software);
        
        //savebutton.state().reset();
        savebutton.setEnabled(false);
        
        //TODO: Save should reset invalid entries ?
        //piechart.fillCategories(true);
        //piechart.setActiveCategoryId(piechart.getActiveCategoryId(), false);
      }
      @Override
      public void onFailure(Throwable exception) { 
        savebutton.state().reset();
      }
    });    
  }
  
  @UiHandler("reloadbutton")
  public void onReload(ClickEvent event) {
    initSoftware(softwarename, true);
    //History.replaceItem(History.getToken(), false);
  }
  
  @UiHandler("softwareform")
  void onSoftwareChange(SoftwareChangeEvent event) {
    software = event.getSoftware();
    software.setDirty(true);
    savebutton.setEnabled(true);
    softwarename = software.getName();
    piechart.setSoftware(software);
    barchart.setSoftware(software);
    softwareform.setSoftware(software);
    piechart.fillCategories();
    barchart.fillCategories();
    piechart.setActiveCategoryId(piechart.getActiveCategoryId(), false);
  }
  
  @UiHandler("softwareform")
  void onPluginResponse(PluginResponseEvent event) {
    PluginResponse response = event.getPluginResponse();
    notifications.addPluginResponse(response, softwareform);
  }
  
  void pieCategorySelected(String categoryId) {
    // Show transition if bar and form aren't visible
    if(!barchart.isVisible() && !softwareform.isVisible()) {
      easeIn(piechart);
    }
    
    sidecolumn.setSize("XS_12");
    piecolumn.setSize("XS_5 SM_4 MD_3");
    piecolumn.setOffset("");
    barcolumn.setSize("XS_7 SM_6 MD_5 LG_4");
    
    barchart.drawCategories(categoryId);
    barchart.fillCategories(false);
    easeIn(barchart);
    barchart.setVisible(true);
    heading.setVisible(false);

    softwareform.setVisible(false);
    
    piechart.updateDimensions();
    barchart.updateDimensions();
  }
  
  void barCategorySelected(String categoryId) {
    sidecolumn.setSize("XS_12 SM_4 MD_3");
    piecolumn.setSize("XS_5 SM_12");
    piecolumn.setOffset("");
    barcolumn.setSize("XS_7 SM_12");
    
    softwareform.showCategoryItems(categoryId);
    easeIn(softwareform);
    softwareform.setVisible(true);
    
    barchart.updateDimensions();
  }
  
  private void setBreadCrumbs() {
    breadcrumbs.clear();
    breadcrumbs.setVisible(true);
    
    String swlabel = piechart.getSoftware().getLabel();
    if (swlabel == null)
      swlabel = piechart.getSoftware().getName();
    AnchorListItem anchor = new AnchorListItem(swlabel);
    anchor.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        History.newItem(NameTokens.browse + "/" + softwarename);
      }
    });
    anchor.setStyleName("first-crumb");
    breadcrumbs.add(anchor);

    if(piechart != null && piechart.getSoftware() != null) {
      anchor = new AnchorListItem("Edit");
      anchor.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          History.replaceItem(NameTokens.publish + "/" + softwarename, false);
          clear();
          initialDraw();
          //initSoftware(softwarename);
        }
      });
      anchor.setStyleName("");
      breadcrumbs.add(anchor);
    }    
    if(piechart != null) {
      final String catid = piechart.getActiveCategoryId();
      if(catid != null) {
        MetadataCategory mcat = this.vocabulary.getCategory(catid);
        anchor = new AnchorListItem(mcat.getLabel()); 
        anchor.addClickHandler(new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            barchart.setActiveCategoryId(null, false);
            onPieSelect(new CategorySelectionEvent(catid));
          }
        });
        anchor.setStyleName("");
        breadcrumbs.add(anchor);
      }
    }
    if(barchart != null) {
      final String catid = barchart.getActiveCategoryId();
      if(catid != null) {
        MetadataCategory mcat = this.vocabulary.getCategory(catid);
        anchor = new AnchorListItem(mcat.getLabel()); 
        anchor.addClickHandler(new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            onBarSelect(new CategorySelectionEvent(catid));
          }
        });
        anchor.setStyleName("");
        breadcrumbs.add(anchor);
      }
    }
    if(anchor != null)
      anchor.addStyleName("active-crumb");
  }
  
  private void easeIn(Widget w) {
    D3.select(w.getElement()).style("opacity", 0);
    D3.select(w.getElement()).transition().duration(400).style("opacity", 1);
  }
  
  @UiHandler("permbutton")
  void onPermButtonClick(ClickEvent event) {
    permissiondialog.show();
    userlist.setVisible(true);
    permlist.setVisible(true);
    if (userlist.getItemCount() == 0)
      setUserList();
    if (permlist.getItemCount() == 0)
      setPermissionList();
  }
  
  @UiHandler("userlist")
  void onUserChangedEvent(ChangeEvent event) {
    permlist.setEnabled(true);
    setpermbutton.setEnabled(true);
    int index = userlist.getSelectedIndex();
    String newuser = userlist.getValue(index);
    selectPermissionForUser(newuser);
  }
  
  private void selectAccessLevel(String accesslevel)
  {
    for (int i = 0; i < permlist.getItemCount(); i++) {
      if (permlist.getItemText(i).equals(accesslevel)) {
        permlist.setSelectedIndex(i);
        break;
      }
    }
  }
  
  private void selectPermissionForUser(final String username)
  {
    SoftwareREST.getSoftwareAccessLevelForUser(software.getName(), 
      username, new Callback<AccessMode, Throwable>() {
      @Override
      public void onFailure(Throwable reason) {
        AppNotification.notifyFailure(reason.getMessage());
      }

      @Override
      public void onSuccess(AccessMode accessmode) {
        selectAccessLevel(accessmode.getMode());
      }
    });
	  
	  if (software.getPermission().getOwner().getName().equals(username)) {
      permlist.setEnabled(false);
      setpermbutton.setEnabled(false);		  
    } else {
      UserREST.getUserRoles(username, new Callback<List<String>, Throwable>() {
        @Override
        public void onFailure(Throwable reason) {
          AppNotification.notifyFailure(reason.getMessage());
        }

        @Override
        public void onSuccess(List<String> roles) {
          if (roles.contains("admin")) {
            permlist.setEnabled(false);
            setpermbutton.setEnabled(false);
          }
        }
      });		  
    }	  
  }
  
  private void setUserList() {
    userlist.clear();
    UserREST.getUsers(new Callback<List<String>, Throwable>() {
      @Override
      public void onFailure(Throwable reason) {
        AppNotification.notifyFailure(reason.getMessage());
      }

      @Override
      public void onSuccess(List<String> list) {
        int i=0;
        for(String name : list) {
          userlist.addItem(name);
          if(name.equals(loggedinuser)) {
            userlist.setItemSelected(i, true);
            selectPermissionForUser(loggedinuser);
          }
          i++;
        }
      }
    });
  }
  
  private void setPermissionList() {
    permlist.clear();
    SoftwareREST.getPermissionTypes(new Callback<List<String>, Throwable>() {
      @Override
      public void onFailure(Throwable reason) {
        AppNotification.notifyFailure(reason.getMessage());
      }

      @Override
      public void onSuccess(List<String> list) {
        for(String name : list) {
          permlist.addItem(name);
        }
      }
    });	  
  }
  
  @UiHandler("setpermbutton")
  void onSetPermissionButtonClick(ClickEvent event) {
    submitPermissionForm();
    event.stopPropagation();
  }
  
  private void submitPermissionForm() {
    final String username = userlist.getSelectedValue();
    final String permtype = permlist.getSelectedValue();
    String ownername = software.getPermission().getOwner().getName();
    UserSession session = SessionStorage.getSession();

    if ((session != null && session.getRoles().contains("admin")) || 
      ownername.equals(this.loggedinuser)) {
      Authorization authorization = new Authorization();
      authorization.setId("");
      authorization.setAgentId("");
      authorization.setAccessToObjId(software.getId());
      authorization.setAgentName(username);
      AccessMode mode = new AccessMode();
      mode.setMode(permtype);
      authorization.setAccessMode(mode);

      SoftwareREST.setSoftwarePermissionForUser(software.getName(), authorization, 
        new Callback<Boolean, Throwable>() {
          @Override
          public void onFailure(Throwable reason) {
            permissiondialog.hide();
            AppNotification.notifyFailure(reason.getMessage());
          }
        
          @Override
          public void onSuccess(Boolean success) {
            permissiondialog.hide();
            AppNotification.notifySuccess("Permission updated!", 2000);
          }
      });
    } else {
      AppNotification.notifyFailure("Not Allowed!");
    }
  }
  
  @UiHandler("cancelbutton")
  void onCancelButtonClick(ClickEvent event) {
    permissiondialog.hide();
    event.stopPropagation();
  }
}
