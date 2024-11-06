package org.fin.rest;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import org.fin.dto.Currency;
import org.fin.ejb.forex.MyForexServiceBean;

@Path("/currency")
public class FinancialAPI {

    @Inject
    MyForexServiceBean myForexServiceBean;

    /**
     * Donne les liste des monnaies disponibles
     * @return
     */
    @GET
    @Path("/currencies")
    @Produces({"application/x-ndjson"})
    @Blocking
    public Multi<Object> getCurrencies() {
        return Multi.createFrom().items(myForexServiceBean.currencies().stream());
    }
}
