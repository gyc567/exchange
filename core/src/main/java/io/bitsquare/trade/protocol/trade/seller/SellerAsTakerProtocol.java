/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.trade.seller;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.MailboxMessage;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.MessageHandler;
import io.bitsquare.p2p.Peer;
import io.bitsquare.trade.SellerAsTakerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.TradeProtocol;
import io.bitsquare.trade.protocol.trade.messages.DepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.messages.FiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.messages.RequestPayDepositMessage;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.trade.protocol.trade.seller.tasks.SellerCommitDepositTx;
import io.bitsquare.trade.protocol.trade.seller.tasks.SellerCreatesAndSignsContract;
import io.bitsquare.trade.protocol.trade.seller.tasks.SellerCreatesAndSignsDepositTx;
import io.bitsquare.trade.protocol.trade.seller.tasks.SellerProcessDepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.seller.tasks.SellerProcessFiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.seller.tasks.SellerProcessRequestPayDepositMessage;
import io.bitsquare.trade.protocol.trade.seller.tasks.SellerSendsPayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.seller.tasks.SellerSendsRequestPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.seller.tasks.SellerSignsAndPublishPayoutTx;
import io.bitsquare.trade.protocol.trade.seller.tasks.SendRequestDepositTxInputsMessage;
import io.bitsquare.trade.protocol.trade.shared.models.ProcessModel;
import io.bitsquare.trade.protocol.trade.shared.taker.tasks.BroadcastTakeOfferFeeTx;
import io.bitsquare.trade.protocol.trade.shared.taker.tasks.CreateTakeOfferFeeTx;
import io.bitsquare.trade.protocol.trade.shared.taker.tasks.VerifyOfferFeePayment;
import io.bitsquare.trade.protocol.trade.shared.taker.tasks.VerifyOffererAccount;
import io.bitsquare.trade.states.TakerState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.util.Validator.nonEmptyStringOf;

public class SellerAsTakerProtocol implements TradeProtocol {
    private static final Logger log = LoggerFactory.getLogger(SellerAsTakerProtocol.class);

    private final SellerAsTakerTrade sellerAsTakerTrade;
    private final ProcessModel processModel;
    private final MessageHandler messageHandler;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerAsTakerProtocol(SellerAsTakerTrade trade) {
        log.debug("New SellerAsTakerProtocol " + this);
        this.sellerAsTakerTrade = trade;
        processModel = trade.getProcessModel();

        messageHandler = this::handleMessage;
        processModel.getMessageService().addMessageHandler(messageHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void cleanup() {
        log.debug("cleanup " + this);
        processModel.getMessageService().removeMessageHandler(messageHandler);
    }

    public void setMailboxMessage(MailboxMessage mailboxMessage) {
        log.debug("setMailboxMessage " + mailboxMessage);
        // Might be called twice, so check that its only processed once
        if (processModel.getMailboxMessage() == null) {
            processModel.setMailboxMessage(mailboxMessage);
            if (mailboxMessage instanceof FiatTransferStartedMessage) {
                handleFiatTransferStartedMessage((FiatTransferStartedMessage) mailboxMessage);
            }
            else if (mailboxMessage instanceof DepositTxPublishedMessage) {
                handleDepositTxPublishedMessage((DepositTxPublishedMessage) mailboxMessage);
            }
        }
    }

    public void takeAvailableOffer() {
        TaskRunner<Trade> taskRunner = new TaskRunner<>(sellerAsTakerTrade,
                () -> log.debug("taskRunner at takeAvailableOffer completed"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                CreateTakeOfferFeeTx.class,
                BroadcastTakeOfferFeeTx.class,
                SendRequestDepositTxInputsMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleRequestTakerDepositPaymentMessage(RequestPayDepositMessage tradeMessage) {
        processModel.setTradeMessage(tradeMessage);

        TaskRunner<Trade> taskRunner = new TaskRunner<>(sellerAsTakerTrade,
                () -> log.debug("taskRunner at handleTakerDepositPaymentRequestMessage completed"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                SellerProcessRequestPayDepositMessage.class,
                VerifyOffererAccount.class,
                SellerCreatesAndSignsContract.class,
                SellerCreatesAndSignsDepositTx.class,
                SellerSendsRequestPublishDepositTxMessage.class
        );
        taskRunner.run();
    }

    private void handleDepositTxPublishedMessage(DepositTxPublishedMessage tradeMessage) {
        processModel.setTradeMessage(tradeMessage);

        TaskRunner<SellerAsTakerTrade> taskRunner = new TaskRunner<>(sellerAsTakerTrade,
                () -> log.debug("taskRunner at handleDepositTxPublishedMessage completed"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                SellerProcessDepositTxPublishedMessage.class,
                SellerCommitDepositTx.class
        );
        taskRunner.run();
    }

    private void handleFiatTransferStartedMessage(FiatTransferStartedMessage tradeMessage) {
        processModel.setTradeMessage(tradeMessage);

        TaskRunner<SellerAsTakerTrade> taskRunner = new TaskRunner<>(sellerAsTakerTrade,
                () -> log.debug("taskRunner at handleFiatTransferStartedMessage completed"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(SellerProcessFiatTransferStartedMessage.class);
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer received" button, so we release the funds for pay out
    public void onFiatPaymentReceived() {
        sellerAsTakerTrade.setProcessState(TakerState.ProcessState.FIAT_PAYMENT_RECEIVED);

        TaskRunner<SellerAsTakerTrade> taskRunner = new TaskRunner<>(sellerAsTakerTrade,
                () -> {
                    log.debug("taskRunner at handleFiatReceivedUIEvent completed");

                    // we are done!
                    processModel.onComplete();
                },
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                VerifyOfferFeePayment.class,
                SellerSignsAndPublishPayoutTx.class,
                SellerSendsPayoutTxPublishedMessage.class
        );
        taskRunner.run();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Massage dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleMessage(Message message, Peer sender) {
        log.trace("handleNewMessage: message = " + message.getClass().getSimpleName() + " from " + sender);
        if (message instanceof TradeMessage) {
            TradeMessage tradeMessage = (TradeMessage) message;
            nonEmptyStringOf(tradeMessage.tradeId);

            if (tradeMessage.tradeId.equals(processModel.getId())) {
                if (tradeMessage instanceof RequestPayDepositMessage) {
                    handleRequestTakerDepositPaymentMessage((RequestPayDepositMessage) tradeMessage);
                }
                else if (tradeMessage instanceof DepositTxPublishedMessage) {
                    handleDepositTxPublishedMessage((DepositTxPublishedMessage) tradeMessage);
                }
                else if (tradeMessage instanceof FiatTransferStartedMessage) {
                    handleFiatTransferStartedMessage((FiatTransferStartedMessage) tradeMessage);
                }
                else {
                    log.error("Incoming message not supported. " + tradeMessage);
                }
            }
        }
    }

    private void handleTaskRunnerFault(String errorMessage) {
        log.error(errorMessage);
        cleanup();
    }

}