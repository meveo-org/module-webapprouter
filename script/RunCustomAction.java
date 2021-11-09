package org.meveo.script;

import java.util.HashMap;
import java.util.Map;
import org.meveo.admin.exception.BusinessException;
import org.meveo.api.exception.EntityDoesNotExistsException;
import org.meveo.elresolver.ELException;
import org.meveo.model.crm.custom.EntityCustomAction;
import org.meveo.model.customEntities.CustomEntityInstance;
import org.meveo.model.customEntities.CustomEntityTemplate;
import org.meveo.model.ICustomFieldEntity;
import org.meveo.model.IEntity;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.storage.Repository;
import org.meveo.persistence.CrossStorageService;
import org.meveo.service.base.MeveoValueExpressionWrapper;
import org.meveo.service.custom.EntityCustomActionService;
import org.meveo.service.custom.CustomEntityTemplateService;
import org.meveo.service.script.CustomScriptService;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.meveo.service.script.ScriptInstanceService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunCustomAction extends Script {
  private static final String MESSAGE = "message";
  private static final Logger LOG = LoggerFactory.getLogger(RunCustomAction.class);
  private EntityCustomActionService ecaService = getCDIBean(EntityCustomActionService.class);
  private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
  private ScriptInstanceService scriptInstanceService = getCDIBean(ScriptInstanceService.class);
  private CrossStorageService crossStorageService = getCDIBean(CrossStorageService.class);
  private CustomEntityTemplateService cetService = getCDIBean(CustomEntityTemplateService.class);

  private Repository repository;
  private String actionCode;
  private String entityCode;
  private String uuid;
  private Map<String, Object> result;

  public String getActionCode() {
    return actionCode;
  }

  public void setActionCode(String actionCode) {
    this.actionCode = actionCode;
  }

  public String getEntityCode() {
    return entityCode;
  }

  public void setEntityCode(String entityCode) {
    this.entityCode = entityCode;
  }

  public String getUuid() {
    return uuid;
  }

  public void setUuid(String uuid) {
    this.uuid = uuid;
  }

  public Repository getDefaultRepository() {
    if (repository == null) {
      repository = repositoryService.findDefaultRepository();
    }
    return repository;
  }

  public Map<String, Object> getResult() {
    return result;
  }

  RunCustomAction() {
    super();
    this.actionCode = "";
    this.entityCode = "";
    this.uuid = "";
    this.result = new HashMap<>();
  }

  @Override
  public void execute(Map<String, Object> parameters) throws BusinessException {
    super.execute(parameters);
    try {
      CustomEntityTemplate entityTemplate = cetService.findByCodeOrDbTablename(this.entityCode);
      Map<String, Object> entity = crossStorageService
          .find(this.getDefaultRepository(), entityTemplate, this.uuid, true);
      if (entity == null) {
        throw new BusinessException(
            "Entity not found. CET: " + this.entityCode + " UUID: " + this.uuid);
      }
      LOG.debug("entity: {}", entity);
      LOG.debug("parameters: {}", parameters);
      LOG.debug("this.actionCode: {}", this.actionCode);
      LOG.debug("this.entityCode: {}", this.entityCode);
      LOG.debug("this.uuid: {}", this.uuid);

      EntityCustomAction action = ecaService.findByCode(this.actionCode);
      if (action == null) {
        throw new BusinessException("Action not found: " + this.actionCode);
      }
      Map<String, Object> context = new HashMap<>();
      context.put(Script.CONTEXT_ACTION, this.actionCode);
      Map<Object, Object> elContext = new HashMap<>(context);
      elContext.put("entity", entity);

      LOG.debug("action: {}", action);
      LOG.debug("context: {}", context);
      LOG.debug("elContext: {}", elContext);

      action.getScriptParameters().forEach((key, value) -> {
        try {
          context.put(key, MeveoValueExpressionWrapper
              .evaluateExpression(value, elContext, Object.class));
        } catch (ELException e) {
          LOG.error("Failed to evaluate el for custom action", e);
        }
      });

      Map<String, Object> scriptResult = scriptInstanceService
          .execute(
              (IEntity) CEIUtils.pojoToCei(entity),
              this.getDefaultRepository(),
              action.getScript().getCode(),
              context);

      // Display a message accordingly on what is set in result
      if (scriptResult.containsKey(Script.RESULT_GUI_MESSAGE_KEY)) {
        result.put(MESSAGE, (String) result.get(Script.RESULT_GUI_MESSAGE_KEY));
      } else if (scriptResult.containsKey(Script.RESULT_GUI_MESSAGE)) {
        result.put(MESSAGE, (String) result.get(Script.RESULT_GUI_MESSAGE));
      } else {
        result.put(MESSAGE, "Action executed successfully");
      }

      if (scriptResult.containsKey(Script.RESULT_GUI_OUTCOME)) {
        result.put("value", (String) result.get(Script.RESULT_GUI_OUTCOME));
      }

      LOG.info(action.getLabel() + " executed successfully");

    } catch (BusinessException | EntityDoesNotExistsException e) {
      LOG.error("Failed to execute a script {} on entity {}", this.actionCode, this.entityCode, e);
    }

  }
}