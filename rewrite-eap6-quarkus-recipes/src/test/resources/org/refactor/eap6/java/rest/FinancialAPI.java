package org.fin.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import java.util.List;
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
    @Produces({"application/json"})
    public List<Object> getCurrencies() {
        return myForexServiceBean.currencies();
    }
}
