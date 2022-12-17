package io.adabox.airdrop;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.koios.Constants;
import com.bloxbean.cardano.client.backend.koios.KoiosBackendService;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.*;
import com.bloxbean.cardano.client.function.helper.ChangeOutputAdjustments;
import com.bloxbean.cardano.client.function.helper.FeeCalculators;
import com.bloxbean.cardano.client.function.helper.InputBuilders;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.AssetUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.bloxbean.cardano.client.util.Tuple;

import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Scanner;

import static com.bloxbean.cardano.client.function.helper.SignerProviders.signerFrom;

public class Main {

    private final BackendService backendService;
    private final TransactionService transactionService;
    private final UtxoSupplier utxoSupplier;
    private final ProtocolParams protocolParams;

    public Main() throws ApiException {
        backendService = new KoiosBackendService(Constants.KOIOS_MAINNET_URL);
        transactionService = backendService.getTransactionService();
        utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        protocolParams = backendService.getEpochService().getProtocolParameters().getValue();
    }

    public static void main(String[] args) throws CborSerializationException, ApiException, FileNotFoundException, URISyntaxException {
        Main mai = new Main();
        mai.transferMultiAssetMultiPayments_whenSingleSender_multipleToken();
    }

    public void transferMultiAssetMultiPayments_whenSingleSender_multipleToken() throws FileNotFoundException, URISyntaxException, CborSerializationException, ApiException {
        String senderMnemonic = "mnemonic"; //TODO Seed Phrase
        Account sender = new Account(Networks.mainnet(), senderMnemonic);
        String senderAddress = sender.baseAddress();
        TxOutputBuilder txOutputBuilder = (txBuilderContext, list) -> {};

        //System.out.println(senderAddress);

        Scanner scanner = new Scanner(getFileFromResource("file1.txt"));
        String policyId = "8e51398904a5d3fc129fbf4f1589701de23c7824d5c90fdb9490e15a";
        String assetName = "434841524c4933";
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            String[] parts = line.split(",");
            System.out.println(parts[0]);
            System.out.println(parts[1]);

            Tuple<String, String> policyAssetName = AssetUtil.getPolicyIdAndAssetName(policyId+assetName);
            Output output = Output.builder()
                    .address(parts[0])
                    .qty((new BigDecimal(parts[1]).multiply(BigDecimal.valueOf(1000000.0))).toBigInteger())
                    .policyId(policyAssetName._1)
                    .assetName(policyAssetName._2).build();

            txOutputBuilder = txOutputBuilder.and(output.outputBuilder());
        }

        TxBuilder builder = txOutputBuilder
                .buildInputs(InputBuilders.createFromSender(senderAddress, senderAddress))
                .andThen(FeeCalculators.feeCalculator(senderAddress, 1))
                .andThen(ChangeOutputAdjustments.adjustChangeOutput(senderAddress, 1));

        Transaction signedTransaction = TxBuilderContext.init(utxoSupplier,protocolParams)
                .buildAndSign(builder, signerFrom(sender));

        System.out.println(signedTransaction);

        Result<String> result = backendService.getTransactionService().submitTransaction(signedTransaction.serialize());
        System.out.println(result);

        waitForTransaction(result);
    }

    public void waitForTransaction(Result<String> result) {
        try {
            if (result.isSuccessful()) { //Wait for transaction to be mined
                int count = 0;
                while (count < 60) {
                    Result<TransactionContent> txnResult = transactionService.getTransaction(result.getValue());
                    if (txnResult.isSuccessful()) {
                        System.out.println(JsonUtil.getPrettyJson(txnResult.getValue()));
                        break;
                    } else {
                        System.out.println("Waiting for transaction to be mined ....");
                    }

                    count++;
                    Thread.sleep(2000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private File getFileFromResource(String fileName) throws URISyntaxException {
        ClassLoader classLoader = getClass().getClassLoader();
        URL resource = classLoader.getResource(fileName);
        if (resource == null) {
            throw new IllegalArgumentException("file not found! " + fileName);
        } else {
            // failed if files have whitespaces or special characters
            //return new File(resource.getFile());
            return new File(resource.toURI());
        }
    }
}
