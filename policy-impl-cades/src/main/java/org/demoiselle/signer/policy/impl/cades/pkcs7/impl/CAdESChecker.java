/*
 * Demoiselle Framework
 * Copyright (C) 2016 SERPRO
 * ----------------------------------------------------------------------------
 * This file is part of Demoiselle Framework.
 *
 * Demoiselle Framework is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License version 3
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License version 3
 * along with this program; if not,  see <http://www.gnu.org/licenses/>
 * or write to the Free Software Foundation, Inc., 51 Franklin Street,
 * Fifth Floor, Boston, MA  02110-1301, USA.
 * ----------------------------------------------------------------------------
 * Este arquivo é parte do Framework Demoiselle.
 *
 * O Framework Demoiselle é um software livre; você pode redistribuí-lo e/ou
 * modificá-lo dentro dos termos da GNU LGPL versão 3 como publicada pela Fundação
 * do Software Livre (FSF).
 *
 * Este programa é distribuído na esperança que possa ser útil, mas SEM NENHUMA
 * GARANTIA; sem uma garantia implícita de ADEQUAÇÃO a qualquer MERCADO ou
 * APLICAÇÃO EM PARTICULAR. Veja a Licença Pública Geral GNU/LGPL em português
 * para maiores detalhes.
 *
 * Você deve ter recebido uma cópia da GNU LGPL versão 3, sob o título
 * "LICENCA.txt", junto com esse programa. Se não, acesse <http://www.gnu.org/licenses/>
 * ou escreva para a Fundação do Software Livre (FSF) Inc.,
 * 51 Franklin St, Fifth Floor, Boston, MA 02111-1301, USA.
 */
package org.demoiselle.signer.policy.impl.cades.pkcs7.impl;

import java.io.IOException;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1UTCTime;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessable;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignerDigestMismatchException;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.tsp.TSPException;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.util.Store;
import org.demoiselle.signer.core.CertificateManager;
import org.demoiselle.signer.core.ca.manager.CAManager;
import org.demoiselle.signer.core.exception.CertificateCoreException;
import org.demoiselle.signer.core.exception.CertificateValidatorCRLException;
import org.demoiselle.signer.core.exception.CertificateValidatorException;
import org.demoiselle.signer.core.util.MessagesBundle;
import org.demoiselle.signer.core.validator.CRLValidator;
import org.demoiselle.signer.core.validator.PeriodValidator;
import org.demoiselle.signer.policy.engine.asn1.etsi.ObjectIdentifier;
import org.demoiselle.signer.policy.engine.asn1.etsi.SignaturePolicy;
import org.demoiselle.signer.policy.engine.factory.PolicyFactory;
import org.demoiselle.signer.policy.impl.cades.SignatureInformations;
import org.demoiselle.signer.policy.impl.cades.SignerException;
import org.demoiselle.signer.policy.impl.cades.pkcs7.PKCS7Checker;
import org.demoiselle.signer.timestamp.Timestamp;
import org.demoiselle.signer.timestamp.connector.TimeStampOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * Basic Implementation for digital signatures in PKCS7 Format.
 * 
 */
public class CAdESChecker implements PKCS7Checker {

	private static final Logger logger = LoggerFactory.getLogger(CAdESSigner.class);

	
	private SignaturePolicy signaturePolicy = null;
	private static MessagesBundle cadesMessagesBundle = new MessagesBundle();
	private byte[] hash = null;
	private Map<String, byte[]> hashes = new HashMap<String, byte[]>();
	private boolean checkHash = false;
	private List<SignatureInformations> signaturesInfo = new ArrayList<SignatureInformations>();
	private String policyName;
	private CertificateManager certificateManager;


	public CAdESChecker() {
		super();
	}

	/**
	 * Validation is done only on digital signatures with a single signer. Valid
	 * only with content of type DATA.: OID ContentType 1.2.840.113549.1.9.3 =
	 * OID Data 1.2.840.113549.1.7.1
	 *
	 * @params content Is only necessary to inform if the PKCS7 package is NOT
	 *         ATTACHED type. If it is of type attached, this parameter will be
	 *         replaced by the contents of the PKCS7 package.
	 * @params signedData Value in bytes of the PKCS7 package, such as the
	 *         contents of a ".p7s" file. It is not only signature as in the
	 *         case of PKCS1.
	 */
	// TODO: Implementar validação de co-assinaturas

	public boolean check(byte[] content, byte[] signedData) throws SignerException{
		Security.addProvider(new BouncyCastleProvider());
		CMSSignedData cmsSignedData = null;
		try {
			if (content == null) {
				if (this.checkHash){
					cmsSignedData = new CMSSignedData(this.hashes, signedData);
					this.checkHash = false;
				}else{
					cmsSignedData = new CMSSignedData(signedData);
				}
				
			} else {
				cmsSignedData = new CMSSignedData(new CMSProcessableByteArray(content), signedData);
			}
		} catch (CMSException ex) {
			throw new SignerException(cadesMessagesBundle.getString("error.invalid.bytes.pkcs7"), ex);
		}

		// Quantidade inicial de assinaturas validadas
		int verified = 0;

		Store<?> certStore = cmsSignedData.getCertificates();
		SignerInformationStore signers = cmsSignedData.getSignerInfos();
		Iterator<?> it = signers.getSigners().iterator();

		// Realização da verificação básica de todas as assinaturas
		while (it.hasNext()) {
			SignatureInformations signatureInfo = new SignatureInformations();
			try {
				SignerInformation signerInfo = (SignerInformation) it.next();
				SignerInformationStore signerInfoStore = signerInfo.getCounterSignatures();
				
				logger.info("Foi(ram) encontrada(s) " + signerInfoStore.size() + " contra-assinatura(s).");

				@SuppressWarnings("unchecked")
				Collection<?> certCollection = certStore.getMatches(signerInfo.getSID());

				Iterator<?> certIt = certCollection.iterator();
				X509CertificateHolder certificateHolder = (X509CertificateHolder) certIt.next();
				
				X509Certificate varCert = new JcaX509CertificateConverter().getCertificate(certificateHolder);
				PeriodValidator pV = new PeriodValidator();				
				try{
					pV.validate(varCert);
			
				}catch (CertificateValidatorException cve) {
					signatureInfo.getValidatorErrors().add(cve.getMessage());
				}
				
				CRLValidator cV = new CRLValidator();				
				try {
					cV.validate(varCert);	
				}catch (CertificateValidatorCRLException cvce) {
					signatureInfo.getValidatorErrors().add(cvce.getMessage());
				}
				
				if (signerInfo.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(certificateHolder))) {
					verified++;
					logger.info(cadesMessagesBundle.getString("info.signature.valid.seq", verified));
				}				
			
				// recupera atributos assinados
				logger.info(cadesMessagesBundle.getString("info.signed.attribute"));
				AttributeTable signedAttributes = signerInfo.getSignedAttributes();
				if ((signedAttributes == null) || (signedAttributes != null && signedAttributes.size() == 0)) {
					signatureInfo.getValidatorErrors().add(cadesMessagesBundle.getString("error.signed.attribute.table.not.found"));
				}
				
				// Validando atributos assinados de acordo com a politica
				Attribute idSigningPolicy = null;
				idSigningPolicy = signedAttributes.get(new ASN1ObjectIdentifier(PKCSObjectIdentifiers.id_aa_ets_sigPolicyId.getId()));
				if (idSigningPolicy == null) {
						signatureInfo.getValidatorErrors().add(cadesMessagesBundle.getString("error.pcks7.attribute.not.found", "idSigningPolicy"));
				}else{
					for (Enumeration<?> p = idSigningPolicy.getAttrValues().getObjects(); p.hasMoreElements();){
						String policyOnSignature = p.nextElement().toString();
						for (PolicyFactory.Policies pv : PolicyFactory.Policies.values()){
							if (policyOnSignature.contains(pv.getUrl())){
								setSignaturePolicy(pv);
								break;
							}							
						}
					}						
				}
				if (signaturePolicy == null){
					signatureInfo.getValidatorErrors().add(cadesMessagesBundle.getString("error.policy.on.component.not.found", "idSigningPolicy"));
					}
				if (signaturePolicy.getSignPolicyInfo().getSignatureValidationPolicy().getCommonRules()
						.getSignerAndVeriferRules().getSignerRules().getMandatedSignedAttr()
						.getObjectIdentifiers() != null) {
					for (ObjectIdentifier objectIdentifier : signaturePolicy.getSignPolicyInfo()
							.getSignatureValidationPolicy().getCommonRules().getSignerAndVeriferRules().getSignerRules()
							.getMandatedSignedAttr().getObjectIdentifiers()) {
							String oi = objectIdentifier.getValue();
							Attribute signedAtt = signedAttributes.get(new ASN1ObjectIdentifier(oi));
							logger.info(oi);
							if (signedAtt == null){
								signatureInfo.getValidatorErrors().add(cadesMessagesBundle.getString("error.signed.attribute.not.found",oi,signaturePolicy.getSignPolicyInfo().getSignPolicyIdentifier().getValue() ));
							}										
					}
				}
				
				// Mostra data e  hora da assinatura, não é carimbo de tempo
				Attribute timeAttribute = signedAttributes.get(CMSAttributes.signingTime);
				Date dataHora = null;
				if (timeAttribute != null) {
					dataHora = (((ASN1UTCTime) timeAttribute.getAttrValues().getObjectAt(0)).getDate());
					logger.info(cadesMessagesBundle.getString("info.date.utc",dataHora));																
				} else {
					logger.info(cadesMessagesBundle.getString("info.date.utc","N/D"));																
				}

				
				// recupera os atributos NÃO assinados
				logger.info(cadesMessagesBundle.getString("info.unsigned.attribute"));
				AttributeTable unsignedAttributes = signerInfo.getUnsignedAttributes();
				if ((unsignedAttributes == null) || (unsignedAttributes != null && unsignedAttributes.size() == 0)) {
					// Apenas info pois a RB não tem atributos não assinados
					logger.info(cadesMessagesBundle.getString("error.unsigned.attribute.table.not.found"));
				}
				
				// Validando atributos NÃO assinados de acordo com a politica
				if (signaturePolicy.getSignPolicyInfo().getSignatureValidationPolicy().getCommonRules()
						.getSignerAndVeriferRules().getSignerRules().getMandatedUnsignedAttr()
						.getObjectIdentifiers() != null) {
						for (ObjectIdentifier objectIdentifier : signaturePolicy.getSignPolicyInfo()
							.getSignatureValidationPolicy().getCommonRules().getSignerAndVeriferRules().getSignerRules()
							.getMandatedUnsignedAttr().getObjectIdentifiers()) {
							String oi = objectIdentifier.getValue();
							Attribute unSignedAtt = unsignedAttributes.get(new ASN1ObjectIdentifier(oi));
							logger.info(oi);
							if (unSignedAtt == null){
								signatureInfo.getValidatorErrors().add(cadesMessagesBundle.getString("error.unsigned.attribute.not.found",oi,signaturePolicy.getSignPolicyInfo().getSignPolicyIdentifier().getValue() ));
							}
							if (oi.equalsIgnoreCase(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken.getId())){
								//Verificando timeStamp
								try{
										byte[] varSignature = signerInfo.getSignature();
										Timestamp varTimeStampSigner = validateTimestamp(unSignedAtt, varSignature); 
										signatureInfo.setTimeStampSigner(varTimeStampSigner);
								}catch (Exception ex) {
									// nas assinaturas feitas na applet o unsignedAttributes.get gera exceção.						
								}
							}
							if (oi.equalsIgnoreCase("1.2.840.113549.1.9.16.2.25")){
								logger.info("++++++++++  EscTimeStamp ++++++++++++");
							}
						}
				}							
				
				LinkedList<X509Certificate> varChain = (LinkedList<X509Certificate>) CAManager.getInstance().getCertificateChain(varCert);
				signatureInfo.setSignDate(dataHora);
				signatureInfo.setChain(varChain);
				signatureInfo.setSignaturePolicy(signaturePolicy);
				this.getSignaturesInfo().add(signatureInfo);
				
			} catch (OperatorCreationException | java.security.cert.CertificateException ex) {
				signatureInfo.getValidatorErrors().add(ex.getMessage());
			} catch (CMSException ex) {				
				// When file is mismatch with sign
				if (ex instanceof CMSSignerDigestMismatchException)
					signatureInfo.getValidatorErrors().add(cadesMessagesBundle.getString("error.signature.mismatch"));
				else
					signatureInfo.getValidatorErrors().add(cadesMessagesBundle.getString("error.signature.invalid"));
			} catch (ParseException e) {
				signatureInfo.getValidatorErrors().add(e.getMessage());
			}
		}

		logger.info(cadesMessagesBundle.getString("info.signature.verified", verified));
		// TODO Efetuar o parsing da estrutura CMS
		return true;
	}
	
	/**
	 *  validade a timestampo on signature
	 * @param attributeTimeStamp
	 * @param varSignature
	 * @return
	 */
	private Timestamp validateTimestamp(Attribute attributeTimeStamp, byte[] varSignature){
		try {
			TimeStampOperator timeStampOperator = new TimeStampOperator();
			byte [] varTimeStamp = attributeTimeStamp.getAttrValues().getObjectAt(0).toASN1Primitive().getEncoded();
			TimeStampToken timeStampToken = new TimeStampToken(new CMSSignedData(varTimeStamp));
			Timestamp timeStampSigner = new Timestamp(timeStampToken);
			timeStampOperator.validate(varSignature,varTimeStamp , null);
			return timeStampSigner;
		} catch (CertificateCoreException | IOException | TSPException | CMSException e) {
			throw new SignerException(e);
		}		
	}
	
	/**
	 * Return the signed file content attached to the signature.
	 *
	 * @param signed
	 *            Signature and signed content.
	 * @return content for attached signature
	 */
	public byte[] getAttached(byte[] signed) {
		return this.getAttached(signed, true);
	}

	/**
	 * Extracts the signed content from the digital signature structure, if it
	 * is a signature with attached content.
	 *
	 * @param signed
	 *            Signature and signed content.
	 * @param validateOnExtract
	 *            TRUE (to execute validation) or FALSE (not execute validation)
	 * 
	 * @return content for attached signature
	 */
	@Override
	public byte[] getAttached(byte[] signed, boolean validateOnExtract) {

		byte[] result = null;

		if (validateOnExtract) {
			this.check(null, signed);
		}

		CMSSignedData signedData = null;
		try {
			signedData = new CMSSignedData(signed);
		} catch (CMSException exception) {
			throw new SignerException(cadesMessagesBundle.getString("error.invalid.bytes.pkcs7"), exception);
		}

		try {
			CMSProcessable contentProcessable = signedData.getSignedContent();
			if (contentProcessable != null) {
				result = (byte[]) contentProcessable.getContent();
			}
		} catch (Exception exception) {
			throw new SignerException(cadesMessagesBundle.getString("error.get.content.pkcs7"), exception);
		}

		return result;

	}		
		
	@Override
	public  List<SignatureInformations> checkAttachedSignature(byte[] signedData){
		if (this.check(null, signedData)){
			return this.getSignaturesInfo();
		}else{
			return null;
		}
	}
    
	@Override
	public  List<SignatureInformations> checkDetattachedSignature(byte[] content, byte[] signedData){
		if (this.check(content, signedData)){
			return this.getSignaturesInfo();
		}else{
			return null;
		}
	}
	
	

	@Override
	public List<SignatureInformations> checkSignatureByHash(String digestAlgorithmOID, byte[] calculatedHashContent, byte[] signedData) throws SignerException{
		this.checkHash = true;
		this.hashes.put(digestAlgorithmOID, calculatedHashContent);
		this.setHash(calculatedHashContent);
		if (this.check(null, signedData)){
			return this.getSignaturesInfo();
		}else{
			return null;
		}		
	}

	private void setSignaturePolicy(PolicyFactory.Policies signaturePolicy) {
		this.setPolicyName(signaturePolicy.name());
		PolicyFactory policyFactory = PolicyFactory.getInstance();
		org.demoiselle.signer.policy.engine.asn1.etsi.SignaturePolicy sp = policyFactory.loadPolicy(signaturePolicy);
		this.signaturePolicy = sp;
	}

	
	
	@Override
	public List<SignatureInformations> getSignaturesInfo() {
		return signaturesInfo;
	}

	public void setSignaturesInfo(List<SignatureInformations> signatureInfo) {
		this.signaturesInfo = signatureInfo;
	}

	public String getPolicyName() {
		return policyName;
	}

	public void setPolicyName(String policyName) {
		this.policyName = policyName;
	}

	public CertificateManager getCertificateManager() {
		return certificateManager;
	}

	public void setCertificateManager(CertificateManager certificateManager) {
		this.certificateManager = certificateManager;
	}

	public byte[] getHash() {
		return hash;
	}

	public void setHash(byte[] hash) {
		this.hash = hash;
	}
	
	
}