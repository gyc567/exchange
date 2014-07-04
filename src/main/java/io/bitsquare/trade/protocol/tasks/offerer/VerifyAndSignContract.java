package io.bitsquare.trade.protocol.tasks.offerer;

import com.google.bitcoin.core.ECKey;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.crypto.CryptoFacade;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.protocol.tasks.FaultHandler;
import io.bitsquare.util.Utilities;
import java.math.BigInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyAndSignContract
{
    private static final Logger log = LoggerFactory.getLogger(VerifyAndSignContract.class);

    public static void run(ResultHandler resultHandler,
                           FaultHandler faultHandler,
                           CryptoFacade cryptoFacade,
                           String accountId,
                           BigInteger tradeAmount,
                           String takeOfferFeeTxId,
                           String messagePubKeyAsHex,
                           Offer offer,
                           String peersAccountId,
                           BankAccount bankAccount,
                           BankAccount peersBankAccount,
                           String takerMessagePubKey,
                           String peersContractAsJson,
                           ECKey registrationKey)
    {
        Contract contract = new Contract(offer,
                tradeAmount,
                takeOfferFeeTxId,
                accountId,
                peersAccountId,
                bankAccount,
                peersBankAccount,
                messagePubKeyAsHex,
                takerMessagePubKey);

        String contractAsJson = Utilities.objectToJson(contract);
        // log.trace("Offerer contract created: " + contract);
        // log.trace("Offerers contractAsJson: " + contractAsJson);
        // log.trace("Takers contractAsJson: " + sharedModel.peersContractAsJson);
        if (contractAsJson.equals(peersContractAsJson))
        {
            log.trace("The 2 contracts as json does match");
            String signature = cryptoFacade.signContract(registrationKey, contractAsJson);
            //log.trace("signature: " + signature);

            resultHandler.onResult(contract, contractAsJson, signature);
        }
        else
        {
            // TODO use diff output as feedback ?
            log.error("Contracts are not matching.");
            log.error("Offerers contractAsJson: " + contractAsJson);
            log.error("Takers contractAsJson: " + peersContractAsJson);

            faultHandler.onFault(new Exception("Contracts are not matching"));
        }
    }

    public interface ResultHandler
    {
        void onResult(Contract contract, String contractAsJson, String signature);
    }
}
