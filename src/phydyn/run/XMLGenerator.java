package phydyn.run;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Runnable;
import beast.evolution.tree.Node;
import beast.util.TreeParser;
import phydyn.analysis.PopModelAnalysis;
import phydyn.analysis.XMLFileWriter;
import phydyn.model.PopModel;

public class XMLGenerator extends Runnable {
	public Input<String> xmlTypeInput = new Input<>("xmlType", "XML Phydyn files: likelihood, modelmcmc, treemcmc", Validate.REQUIRED);
	public Input<String> outputFileInput = new Input<>("outputFile", "Output XML file",Validate.REQUIRED);
	
	public Input<PopModel> modelInput = new Input<>("model","Complex Population Model", Validate.REQUIRED);
	public Input<String> treeFileInput = new Input<>("treeFile", "Newick Tree file");
	// file type. Currently only option is csv (default)
	public Input<Boolean> adjustInput = new Input<>("adjustTipHeights","true if the tip heights should be "
			+ "adjusted to 0 (i.e. contemporaneous) after reading in tree");
	
	public Input<Boolean> createDateTraitInput = new Input<>("createDateTrait","Date tips with Date trait (using t1)",new Boolean(false));
	public Input<Boolean> createTypeTraitInput = new Input<>("createTypeTrait",
			"Create separate type trait using tree tip annotations",new Boolean(false));
	
	//public Input<Priors> priorsInput = new Input<>("priors","Model parameter priors");
	public Input<String> analysisInput = new Input<>("analysis","Model analysis specification");

	public String xmlFile;
	
	private String  xmlType, newick;
	private String treeFileName;
	private TreeParser tree;
	private int numTips;
	private String modelName, taxaID, dateTraitID, typeTraitID, treeID, stlhID;
	private String[] tipNames;
	private Node[] tipNodes;
	private boolean createDateTrait, createTypeTrait;
	private double mrTipDate; // most recent date 
	private double treeHeight;
	private PopModel popModel;
	
	private PopModelAnalysis analysis;
	
	
	final static String LIKELIHOOD = "likelihood", MODELMCMC = "modelmcmc", TREEMCMC = "treemcmc";
	
	/**
	 * initAndValidate is supposed to check validity of values of inputs, and initialise. 
	 * If for some reason this fails, the most appropriate exception to throw is 
	 * IllegalArgumentException (if the combination of input values is not correct)
	 * or otherwise a RuntimeException.
	 */	
	@Override
	public void initAndValidate() {
		xmlType = xmlTypeInput.get().toLowerCase();
		if (xmlType.equals(LIKELIHOOD)) {
			System.out.println("Generating Likelihood test xml");
		} else if (xmlType.equals(MODELMCMC)) {
			System.out.println("Generating XML for population model parameter sampling");
			// throw new IllegalArgumentException("xmlType "+xmlType+" not implemented");
		} else if (xmlType.equals(TREEMCMC)) {
			System.out.println("Generating tree sampling xml");
			throw new IllegalArgumentException("xmlType "+xmlType+" not implemented");
		} else {
			throw new IllegalArgumentException("Incorrect xmlType value. Use "+LIKELIHOOD+"/"+MODELMCMC+"/"+TREEMCMC+"\n");
		}
		
		xmlFile = outputFileInput.get();
		
		String extension = null;
		int i = xmlFile.lastIndexOf('.');
		if (i > 0) {
		    extension = xmlFile.substring(i+1);
		}
		if (extension==null) {
			System.out.println("Warning: File name must have extension 'xml'. Appending extension.");
			xmlFile = xmlFile+".xml";
		}
		System.out.println("output xml file = "+xmlFile);
		
		if (treeFileInput.get()==null)
			treeFileName = null;
		else
			treeFileName = treeFileInput.get();
		
		//String newick0 = "((0xx:1.0,1:1.0)4:1.0,(2:1.0,3:1.0)5:0.5)6:0.0;";
	    //String newick = "(((((((1_I0:5.708227426,2_I1:5.708227426):0.7645709048,3_I1:6.472798331):0.1810747324,(((4_I0:3.988504052,5_I0:3.988504052):0.2501849463,(6_I1:4.088873464,(7_I0:2.183952334,8_I0:2.183952334):1.904921131):0.1498155343):1.781679501,9_I1:6.020368499):0.6335045645):0.269797334,((((10_I0:4.299771899,((11_I0:0.5364754107,12_I0:0.5364754107):2.037616684,13_I1:2.574092095):1.725679804):0.1423644514,(14_I1:2.36409937,15_I0:2.36409937):2.07803698):0.7447825363,(((16_I0:3.544817617,17_I0:3.544817617):0.2782877752,(((18_I0:2.42438363,19_I0:2.42438363):0.9736981544,20_I1:3.398081785):0.06930400453,(21_I1:2.796044548,(((22_I1:1.692894707,23_I0:1.692894707):0.1221909695,24_I1:1.815085676):0.2730292549,(25_I1:1.825877068,26_I1:1.825877068):0.2622378632):0.707929617):0.6713412412):0.3557196028):1.027961986,(27_I0:4.726630545,28_I0:4.726630545):0.1244368334):0.3358515085):0.03544563755,29_I1:5.222364524):1.701305874):0.8462241453,((((30_I0:2.559983679,31_I1:2.559983679):1.675784513,(32_I1:4.176833893,33_I1:4.176833893):0.05893429949):1.080934471,((34_I0:4.453447116,35_I0:4.453447116):0.8181244163,36_I0:5.271571533):0.04513113009):0.2179816965,37_I1:5.534684359):2.235210184):0.4547390334,(((((((38_I0:1.705853569,39_I0:1.705853569):1.930180388,40_I0:3.636033957):2.921691615,41_I1:6.557725572):0.6757145801,((((42_I0:5.794849314,(((43_I0:3.119402965,(44_I1:2.253862139,45_I1:2.253862139):0.8655408264):2.461694149,((46_I1:4.011475231,47_I0:4.011475231):0.4832442061,48_I0:4.494719437):1.086377678):0.06362816758,((((((((49_I0:3.212181289,50_I1:3.212181289):0.05569458448,51_I0:3.267875873):0.04012244278,52_I1:3.307998316):0.1320940505,(53_I0:3.152451882,((54_I0:1.601472767,55_I1:1.601472767):0.3421482705,56_I0:1.943621038):1.208830844):0.2876404843):0.413240715,(57_I0:2.146944884,58_I0:2.146944884):1.706388198):0.5754739146,59_I1:4.428806996):0.3059241507,(60_I0:1.933712552,61_I0:1.933712552):2.801018594):0.4880693423,((62_I0:2.390525579,(63_I0:1.841897371,64_I0:1.841897371):0.5486282084):2.071285382,(65_I1:4.428674333,66_I0:4.428674333):0.0331366283):0.7609895282):0.4219247933):0.1501240316):0.6240591116,((67_I1:1.864922516,68_I0:1.864922516):1.039301162,(69_I0:2.715890368,(70_I1:2.021603108,71_I0:2.021603108):0.6942872601):0.1883333092):3.514684748):0.07544577094,((72_I0:4.209978444,((73_I1:2.076159393,74_I0:2.076159393):0.2423462626,75_I0:2.318505656):1.891472788):0.1897606985,(76_I0:4.397150143,((77_I0:3.293442266,78_I0:3.293442266):0.6411659808,79_I0:3.934608247):0.4625418963):0.002588998802):2.094615054):0.3048433685,80_I1:6.799197565):0.4342425868):0.1406785189,(((((((81_I0:1.679574906,82_I1:1.679574906):2.928047438,(83_I1:3.934325562,(84_I1:3.789399755,85_I0:3.789399755):0.144925807):0.6732967811):0.03109217919,86_I1:4.638714522):0.5227192288,(87_I1:4.213675,(((88_I0:2.548917846,89_I0:2.548917846):0.6758098415,90_I1:3.224727688):0.8876898617,91_I1:4.112417549):0.101257451):0.947758751):0.2515390394,(92_I0:2.087781538,93_I1:2.087781538):3.325191252):0.4892164914,(((94_I1:2.987380944,(95_I1:0.631327652,96_I0:0.631327652):2.356053292):1.706928933,(((97_I1:0.9036075739,98_I0:0.9036075739):2.7939881,(99_I1:3.667861086,100_I1:3.667861086):0.02973458801):0.2220787361,((101_I1:2.157378732,102_I0:2.157378732):1.095786114,103_I0:3.253164845):0.6665095649):0.7746354662):0.5512742672,(104_I0:2.770403003,105_I0:2.770403003):2.475181141):0.6566051383):1.383153141,((((106_I1:3.426563444,((107_I0:3.10959676,108_I0:3.10959676):0.2155172004,109_I1:3.325113961):0.1014494834):1.33439862,((110_I0:2.518132551,111_I1:2.518132551):2.201793436,112_I0:4.719925987):0.041036078):0.9445132237,(113_I0:3.222224616,114_I1:3.222224616):2.483250672):1.021972066,(115_I1:6.10204638,116_I1:6.10204638):0.6254009737):0.5578950688):0.08877624805):0.09596411443,((((117_I0:2.269775948,118_I0:2.269775948):1.035064424,119_I0:3.304840373):3.552652614,(((120_I0:4.067302972,121_I0:4.067302972):2.238551676,(((((((122_I0:0.8051903116,123_I0:0.8051903116):2.280219813,(124_I1:3.063072271,125_I0:3.063072271):0.02233785374):0.7797305829,(126_I0:3.782890397,127_I1:3.782890397):0.08225031104):0.5684724177,128_I0:4.433613125):0.7258122615,(129_I1:4.575869412,(((130_I0:1.479349562,131_I0:1.479349562):0.9641829501,(132_I1:2.390849728,133_I0:2.390849728):0.05268278361):1.952822863,(((134_I0:2.488931456,135_I1:2.488931456):1.238389684,136_I0:3.72732114):0.2365143883,(137_I1:2.249737453,138_I0:2.249737453):1.714098075):0.4325198462):0.1795140372):0.5835559754):0.2105536418,(139_I0:3.449136638,140_I0:3.449136638):1.92084239):0.8829019841,141_I0:6.252881013):0.05297363545):0.0659745961,((((142_I0:4.280078684,143_I0:4.280078684):0.03561384172,144_I0:4.315692526):0.6644283644,((145_I0:3.954762961,(146_I0:0.9709915993,147_I1:0.9709915993):2.983771362):0.141197193,(148_I0:2.729958626,149_I1:2.729958626):1.366001528):0.8841607362):0.3918401182,(((150_I1:4.532317662,(151_I1:4.456943382,152_I0:4.456943382):0.0753742804):0.2490727823,(153_I0:4.376409859,(154_I0:2.298679302,(155_I0:1.488749585,156_I1:1.488749585):0.8099297174):2.077730557):0.4049805855):0.0994368754,(((157_I1:3.799269355,(158_I1:2.826789663,159_I0:2.826789663):0.9724796916):0.3044769787,(160_I1:3.408757996,161_I0:3.408757996):0.6949883372):0.1752681374,((162_I1:2.650829448,163_I1:2.650829448):1.218254962,164_I0:3.86908441):0.4099300606):0.6018128489):0.4911336889):0.9998682357):0.4856637423):0.5512459492,(((165_I0:6.237381758,(((((166_I1:2.496582023,167_I1:2.496582023):0.1004845909,168_I0:2.597066614):0.1460860721,169_I0:2.743152686):2.866918115,(170_I0:4.467235497,171_I0:4.467235497):1.142835304):0.5599935257,(172_I1:4.141896815,173_I1:4.141896815):2.028167512):0.06731743138):0.3466974563,((((174_I0:2.309450306,175_I0:2.309450306):0.8297831876,176_I1:3.139233493):1.634953709,177_I1:4.774187203):0.2822041357,178_I0:5.056391338):1.527687876):0.4079642768,179_I1:6.992043491):0.4166954444):0.06134384933):0.3771778889,(((180_I0:5.632536914,(((181_I1:0.9631864582,182_I0:0.9631864582):3.742320468,183_I1:4.705506926):0.8723777715,(((184_I0:3.955187676,((185_I0:3.585339983,186_I1:3.585339983):0.3606188534,187_I0:3.945958836):0.009228839715):1.06125649,((188_I1:1.827242601,189_I0:1.827242601):1.949342452,190_I1:3.776585053):1.239859113):0.09957402749,191_I1:5.116018193):0.4618665038):0.05465221655):0.7153067652,(((192_I1:2.9516802,193_I1:2.9516802):3.106896002,(((((194_I0:4.048586043,195_I1:4.048586043):0.3709590862,196_I0:4.41954513):0.4670647978,((197_I0:1.801056379,198_I0:1.801056379):2.794897769,199_I0:4.595954148):0.290655779):0.3443724609,(200_I0:2.265565152,201_I0:2.265565152):2.965417236):0.4010695608,(((202_I0:3.436524773,(203_I1:3.277658409,204_I0:3.277658409):0.1588663639):1.609591393,(205_I0:1.83572576,206_I1:1.83572576):3.210390407):0.5778705309,207_I1:5.623986697):0.008065251752):0.426524253):0.1872524216,208_I1:6.245828624):0.1020150554):1.200850039,((209_I1:5.246863407,210_I1:5.246863407):1.141522645,(((211_I1:3.444213426,(212_I0:1.277125142,213_I0:1.277125142):2.167088284):1.152467872,214_I1:4.596681298):1.440641244,((((215_I0:2.730589935,216_I0:2.730589935):0.9953358881,(217_I0:0.1925460831,218_I0:0.1925460831):3.53337974):1.153186451,(((219_I0:3.161485647,220_I0:3.161485647):0.7032416439,221_I1:3.864727291):0.777140002,((222_I1:3.898889289,(223_I0:2.709756762,((224_I0:0.8224339891,225_I1:0.8224339891):1.675953031,226_I0:2.49838702):0.2113697423):1.189132527):0.4959664863,((227_I0:3.751041844,(228_I1:3.218866608,(229_I0:0.1201531793,230_I1:0.1201531793):3.098713428):0.5321752365):0.4613814617,((231_I1:2.887986967,(232_I0:1.993215216,233_I1:1.993215216):0.894771751):0.4445507724,(234_I0:2.911812012,((235_I1:1.120785755,236_I0:1.120785755):1.092690473,237_I0:2.213476228):0.6983357844):0.4207257274):0.8798855663):0.1824324689):0.2470115182):0.2372449816):0.7819193436,((((238_I0:4.606854763,(((239_I0:3.39865845,(240_I0:3.145059488,241_I0:3.145059488):0.2535989617):0.7783536538,242_I0:4.177012104):0.02034126394,(243_I0:0.7117046278,244_I0:0.7117046278):3.48564874):0.4095013959):0.2430394865,245_I0:4.84989425):0.2588366524,((246_I0:2.45435189,247_I0:2.45435189):1.143112502,((248_I0:2.68554122,(249_I1:1.898268462,250_I0:1.898268462):0.787272758):0.440920404,251_I1:3.126461624):0.4710027681):1.511266511):0.3166830704,(252_I0:4.024471186,((253_I0:0.5267442461,254_I1:0.5267442461):3.19754942,(255_I0:3.155069137,256_I0:3.155069137):0.5692245292):0.3001775202):1.400942786):0.2356176455):0.3762909233):0.3510635108):1.160307666):0.298566956):0.3773729024):0.9873075584,(((((((257_I1:6.003481186,(((((258_I0:3.59332293,259_I0:3.59332293):0.1312901867,(260_I0:3.236214587,261_I0:3.236214587):0.4883985301):1.656520788,((262_I1:2.668625069,263_I0:2.668625069):1.725868966,(264_I1:3.454141445,265_I0:3.454141445):0.9403525899):0.9866398697):0.3675220011,((266_I1:4.001983397,267_I1:4.001983397):0.883584128,(268_I0:2.241941654,(269_I0:1.532678285,270_I0:1.532678285):0.7092633687):2.643625871):0.8630883812):0.02026071429,(((((271_I0:2.219232645,272_I0:2.219232645):0.3193042256,273_I1:2.53853687):1.567392584,274_I0:4.105929454):0.5940737179,(275_I0:4.140381646,276_I1:4.140381646):0.5596215256):0.712092928,(277_I1:4.833268016,278_I1:4.833268016):0.5788280837):0.3568205204):0.2345645654):0.3556317378,(279_I1:4.114717069,280_I1:4.114717069):2.244395854):0.009277597977,(((281_I0:4.684549233,(282_I0:3.414082271,283_I0:3.414082271):1.270466963):0.375141131,(((284_I0:2.420483289,(285_I0:2.37111316,286_I1:2.37111316):0.04937012905):0.3380650981,(287_I0:2.401275961,288_I0:2.401275961):0.3572724263):1.772400659,(289_I1:3.361393975,(((290_I1:2.441038991,291_I0:2.441038991):0.0934121875,292_I1:2.534451179):0.396534652,293_I1:2.930985831):0.4304081442):1.169555071):0.5287413182):0.3405840813,(((((294_I0:2.087539489,295_I1:2.087539489):1.400887249,(((296_I1:2.638999486,(297_I0:2.312988605,(298_I0:1.976115334,(299_I0:0.6198674762,300_I0:0.6198674762):1.356247858):0.336873271):0.3260108804):0.6942676186,301_I1:3.333267104):0.04752451466,((302_I0:0.6776321281,303_I0:0.6776321281):2.700888012,304_I1:3.378520141):0.002271478227):0.1076351192):0.3264359331,(305_I1:1.91837232,306_I0:1.91837232):1.896490351):1.101119681,((307_I0:2.087734146,(308_I0:0.7821227398,309_I0:0.7821227398):1.305611406):1.45275643,310_I0:3.540490576):1.375491776):0.004928081622,(311_I0:2.394008144,312_I0:2.394008144):2.52690229):0.4793640118):0.968116076):0.1771184925,((313_I0:3.918948774,(314_I1:3.397001443,315_I1:3.397001443):0.5219473301):1.786198908,((316_I0:5.009643319,(317_I0:3.265379517,318_I0:3.265379517):1.744263801):0.5922633661,(((319_I1:4.387509878,320_I0:4.387509878):0.5427148781,((321_I1:3.569144671,(322_I1:0.7326221845,323_I1:0.7326221845):2.836522487):0.8643812687,(((324_I1:3.560086738,325_I0:3.560086738):0.6793904644,(((326_I0:0.07053641134,327_I1:0.07053641134):3.787174124,328_I1:3.857710535):0.1710723455,329_I0:4.02878288):0.2106943214):0.1033742599,330_I1:4.342851462):0.09067447835):0.4966988164):0.2346460681,(331_I1:4.561583211,(332_I1:4.275044359,333_I0:4.275044359):0.2865388518):0.603287614):0.43703586):0.1032409965):0.840361333):0.8408231194,(((334_I1:3.592775023,335_I0:3.592775023):2.289115114,((336_I0:3.566637181,337_I1:3.566637181):0.8086874088,(338_I0:1.439287572,339_I1:1.439287572):2.936037018):1.506565547):0.05784529239,(340_I1:5.297366973,((341_I0:2.554427069,(342_I0:0.515374767,343_I1:0.515374767):2.039052302):2.59335502,((344_I1:1.541973197,345_I1:1.541973197):1.102503602,346_I0:2.644476799):2.50330529):0.1495848834):0.6423684559):1.446596705):0.6381346573,((((((347_I0:5.123547158,((348_I0:4.38224469,(349_I1:3.107131492,350_I0:3.107131492):1.275113197):0.1286333675,(((((351_I1:3.105210233,352_I0:3.105210233):0.09829227014,353_I1:3.203502503):0.9623130778,(354_I0:3.42849364,355_I0:3.42849364):0.7373219404):0.09840482995,((356_I0:2.861474476,357_I1:2.861474476):0.4611947977,358_I0:3.322669274):0.9415511367):0.2164572041,(359_I1:4.407695649,(360_I0:1.23035676,361_I1:1.23035676):3.177338889):0.07298196578):0.03020044247):0.6126691005):0.7240572446,((362_I1:4.25925642,((((363_I0:2.36272921,364_I1:2.36272921):0.881420563,(365_I0:3.127785223,366_I1:3.127785223):0.1163645505):0.06467421529,(367_I0:1.923997288,368_I0:1.923997288):1.384826701):0.2401811989,369_I0:3.549005187):0.710251233):0.3640759591,370_I0:4.623332379):1.224272023):0.1949734215,(371_I1:4.922387402,372_I1:4.922387402):1.120190422):1.187007556,((373_I0:3.841286588,((374_I0:2.782339994,(375_I0:1.054072636,376_I0:1.054072636):1.728267358):1.045056771,377_I1:3.827396765):0.01388982356):0.5262812324,(((378_I0:2.854980049,379_I1:2.854980049):0.9009604443,((380_I0:1.527331887,(381_I1:0.5897632314,382_I0:0.5897632314):0.9375686555):1.041910635,383_I0:2.569242522):1.186697971):0.5927087783,(384_I0:3.153891074,385_I0:3.153891074):1.194758198):0.0189185484):2.86201756):0.1916279893,((((386_I0:0.8602302523,387_I0:0.8602302523):3.335200491,388_I1:4.195430743):1.10856444,(389_I0:3.436770605,(390_I1:3.435473273,391_I1:3.435473273):0.001297332612):1.867224577):0.2251859255,(392_I1:4.087618328,393_I1:4.087618328):1.44156278):1.892032261):0.2055814203,((((((394_I0:2.124517502,395_I0:2.124517502):1.60375893,(396_I0:2.285148779,397_I0:2.285148779):1.443127653):0.5266658543,((398_I0:2.240089125,399_I0:2.240089125):0.3644880127,400_I0:2.604577138):1.650365147):1.361453255,((401_I0:3.768652696,402_I0:3.768652696):1.387183531,403_I1:5.155836227):0.4605593132):0.9146733992,(404_I0:6.07207809,(((405_I0:3.092446879,406_I1:3.092446879):1.0621638,(407_I1:3.128317412,408_I1:3.128317412):1.026293267):0.4344482746,(409_I1:2.654500805,410_I1:2.654500805):1.934558148):1.483019136):0.4589908498):1.000692825,(((((411_I0:3.716983955,(412_I0:2.533866661,413_I0:2.533866661):1.183117295):0.4440132654,414_I0:4.160997221):0.1911316859,415_I0:4.352128907):0.3254883935,(416_I1:3.653224792,417_I0:3.653224792):1.024392508):2.517214395,((((418_I0:1.705866425,419_I1:1.705866425):4.559500834,(((420_I0:4.589643209,(421_I0:4.013610507,422_I1:4.013610507):0.5760327019):0.3367011467,423_I1:4.926344355):1.078578323,((((424_I0:2.305949227,425_I1:2.305949227):2.928083617,426_I0:5.234032844):0.07095858112,(((427_I0:2.535629066,428_I0:2.535629066):1.473013547,429_I1:4.008642612):0.3698173712,430_I1:4.378459984):0.9265314415):0.08136965976,((431_I1:4.819029897,(432_I1:3.555149739,(433_I0:2.566465258,434_I1:2.566465258):0.988684481):1.263880159):0.3794460628,435_I0:5.19847596):0.187885125):0.6185615934):0.2604445799):0.47049374,((((((436_I1:3.06301464,437_I0:3.06301464):0.7946574835,438_I0:3.857672123):0.2384411736,(439_I0:3.365567482,440_I0:3.365567482):0.7305458151):0.003684238723,((441_I0:3.798110617,442_I1:3.798110617):0.001405896901,(443_I1:2.812291483,444_I1:2.812291483):0.9872250312):0.3002810216):0.5986160804,(((445_I0:2.204867651,446_I0:2.204867651):1.274578398,(447_I0:3.258962438,448_I1:3.258962438):0.2204836109):0.9864772265,(449_I1:3.938350339,450_I0:3.938350339):0.527572937):0.2324903404):0.2381512713,(451_I1:2.83333732,452_I1:2.83333732):2.103227567):1.799296111):0.3741737994,((((((((453_I0:2.569050833,454_I1:2.569050833):0.2861246043,455_I1:2.855175437):0.4078278325,456_I1:3.26300327):1.331889748,457_I1:4.594893018):0.05729407777,458_I1:4.652187096):0.2194746519,(((459_I1:1.519248825,460_I1:1.519248825):0.9698883041,461_I1:2.489137129):0.205630014,462_I1:2.694767143):2.176894605):0.4220166616,463_I0:5.293678409):0.6401457509,464_I0:5.93382416):1.176210637):0.08479689747):0.3369300693):0.09503302527):0.3976720011):0.3198854002,((((((465_I1:3.600580641,(466_I1:3.40602391,(467_I0:0.988413576,468_I1:0.988413576):2.417610334):0.1945567313):0.5799319943,469_I1:4.180512635):0.348134102,470_I0:4.528646737):0.8894856541,(471_I1:4.67386849,472_I0:4.67386849):0.7442639014):0.6042741684,(473_I0:5.290442233,474_I1:5.290442233):0.7319643269):0.07900901805,(((475_I0:2.890158735,476_I0:2.890158735):2.291542078,477_I1:5.181700813):0.8161258255,(((((478_I0:0.1802547203,479_I1:0.1802547203):2.73607353,480_I0:2.91632825):0.8519641242,481_I1:3.768292374):1.247461746,(((482_I1:3.928570321,483_I0:3.928570321):0.01231626295,((484_I0:2.690973663,485_I0:2.690973663):0.7522419892,((486_I1:2.378159444,487_I0:2.378159444):1.037434538,(488_I1:3.376659891,(489_I0:0.5295898051,490_I0:0.5295898051):2.847070086):0.03893409123):0.02762167059):0.4976709313):0.4742008401,491_I1:4.415087424):0.6006666971):0.7932198622,((((492_I0:2.577751916,493_I0:2.577751916):0.01254897337,494_I1:2.590300889):1.726532323,(495_I1:2.01259634,496_I0:2.01259634):2.304236873):0.8432312258,((497_I0:4.327159465,(498_I0:2.652795275,499_I0:2.652795275):1.67436419):0.2574662586,500_I1:4.584625724):0.5754387147):0.6489095446):0.1888526555):0.1035889393):2.242936613):0.8675889439);";
	   
		processTree();
	    
	    modelName = modelInput.get().getName();
	    taxaID = modelName + "-taxa" ;
	    dateTraitID = modelName + "-dates";	    
	    popModel = modelInput.get();
	    
	    // Date trait
	    dateTraitID = modelName + "-dates";	
	    createDateTrait = createDateTraitInput.get();
	    if (createDateTrait) {
	    	if (popModel.hasEndTime()) {
	    		mrTipDate = popModel.getEndTime();
	    		popModel.unsetEndTime();
	    	} else {
	    		mrTipDate = treeHeight;
	    	}
	    }
	    // Type trait
	    typeTraitID = modelName + "-types";	
	    createTypeTrait = createTypeTraitInput.get();
	    
	    // instantiate priors
	    if (analysisInput.get()==null) {
	    	if (xmlType.equals(MODELMCMC)) {
	    		System.out.println("Analysis type requires 'analysis' element");
	    		throw new IllegalArgumentException("Priors missing");
	    	}
	    } else {  // there's analysisInput component
	    	if (xmlType.equals(LIKELIHOOD)) {
	    		System.out.println("Warning: Ignoring analysis component - not needed for Likelihood analysis");
	    	}
	    	try {
	    		analysis = new PopModelAnalysis(analysisInput.get(), popModel, this);
	    	} catch (Exception e) {
	    		analysis = null;
	    	}
	    }
	    
	   
	    
	    
	}
	
	void processTree() {
		 newick = "";
		 try
		 {
			 newick = new String ( Files.readAllBytes( Paths.get(treeFileName) ) );
		 }
		 catch (Exception e)
		 {
			 //e.printStackTrace();
			 throw new IllegalArgumentException("Error while trying to open file <"+treeFileName+">\n");
		 }
		
	    /**
	     * @param newick                a string representing a tree in newick format
	     * @param adjustTipHeights      true if the tip heights should be adjusted to 0 (i.e. contemporaneous) after reading in tree.
	     * @param allowSingleChildNodes true if internal nodes with single children are allowed
	     * @param isLabeled             true if nodes are labeled with taxa labels
	     * @param offset                if isLabeled == false and node labeling starts with x
	     *                              then offset should be x. When isLabeled == true offset should
	     *                              be 1 as by default.
	     */	   
		tree = new TreeParser(newick, adjustInput.get(), false, true, 0);
		Node root = tree.getRoot();
		System.out.println("--- tree file: "+treeFileName);
		//System.out.println("root nr: "+root.getNr());
		System.out.println("root height: "+root.getHeight());
			
		/* Visit Nodes  */
		Node[] nodes = tree.listNodesPostOrder(null, null);
		/* Node[] nodes = tree.getNodesAsArray(); */
		/* List<Node> nodes = root.getAllChildNodes(); */
		numTips = 0;
		List<String> ids = new ArrayList<String>();
		List<Node> leaves = new ArrayList<Node>();
		for(Node node: nodes) {
			if (node.isLeaf()) {
				//System.out.print(node.isLeaf() ? "Leaf     " : "Internal ");
				ids.add(node.getID());
				leaves.add(node);
				//System.out.println(node.getHeight());
				//System.out.print("Nr: "+node.getNr()+ " id:"+node.getID()+" h:"+node.getHeight());
				//System.out.println(" toParent " + node.getLength()+" meta: "+node.metaDataString);
			}
		}			
		numTips = ids.size();
		tipNames = new String[numTips];
		tipNodes = new Node[numTips];
		ids.toArray(tipNames);
		leaves.toArray(tipNodes);
		ids.clear(); leaves.clear();
		// Dates
		treeHeight = root.getHeight();
		System.out.println("num tips = "+numTips);
	}

	@Override
	public void run() throws Exception {	
		
		if ((xmlType.equals(MODELMCMC)) && (analysis==null)) {
			System.out.println("Quitting...");
			return;
		}
		
		// Do not generate trajectory - update(). t1 might not be set.
		System.out.println("-- Population model");
		modelInput.get().printModel();  // print equations 
		
		XMLFileWriter writer = new XMLFileWriter(xmlFile);
		
		
		writeHead(writer);
		writeTaxa(writer);
		if (createDateTrait) {
		 writeDateTrait(writer);
		}
		if (createTypeTrait) {
			writeTypeTrait(writer);
		}
		
		if (xmlType.equals(LIKELIHOOD)) {
			writeLikelihood(writer);
		} else if (xmlType.equals(MODELMCMC)) {			
			writeModelMCMC(writer);
		}
		
		writeTail(writer);
		writer.close();
		
		System.out.println("Output sent to XML file: "+xmlFile);

	}
	

	
	void writeHead(XMLFileWriter writer) throws IOException {
		writer.tabAppend("<?xml version=\"1.0\"?>\n");
		String s = "<beast version=\"2.5\" namespace=\"";
		s += "phydyn.model:phydyn.distribution";
		if (xmlType.equals(LIKELIHOOD)) {
			s += ":phydyn.run";
		}
		if (xmlType.equals(MODELMCMC)) {
			s += ":beast.core";
		}
		s += ":beast.util:beast.evolution.alignment:beast.evolution.tree";
		s += ":beast.evolution.operators";
		s += "\">\n\n";
		writer.tabAppend(s);	
	}
	
	void writeTaxa(XMLFileWriter writer) throws IOException {
		writer.tabAppend("<taxa spec=\"TaxonSet\" id=\""+ taxaID +"\">");
		int three=3;
		for(int i = 0; i < numTips; i++) {
			if (three==3) {
				writer.tabAppend("\n  ");
				three=0;
			}
			writer.tabAppend("<taxon spec=\"Taxon\" id=\""+ tipNames[i] +"\" /> ");
			three++;
		}		
	     writer.tabAppend("\n</taxa>\n");
		
	}
	
	void writeDateTrait(XMLFileWriter writer) throws IOException {
		//<traits spec="TraitSet" id="simpleDateTrait" traitname="date" taxa="@simpletaxa">
		writer.tabAppend("<traits spec=\"TraitSet\" id=\""+dateTraitID+"\" traitname=\"date\" taxa=\"@"+taxaID+"\">");		
		// 1_I0=10,2_I1=10,3_I1=10,4_I0=10,5_I0=10,6_I1=10,7_I0=10 ....
		// first taxon
		writer.tabAppend("\n"+tipNames[0]+"="+(mrTipDate-tipNodes[0].getHeight()));
		int ten=1;
		for(int i = 1; i < numTips; i++) {
			if (ten==10) {
				writer.tabAppend("\n");
				ten=0;
			}
			writer.tabAppend(","+tipNames[i]+"="+(mrTipDate-tipNodes[i].getHeight()));
			ten++;
		}	
		//</traits>
		writer.tabAppend("\n</traits>\n");
	}
	
	void writeTypeTrait(XMLFileWriter writer) throws IOException {
		//<traits spec="TraitSet" id="--" taxa="--">
		writer.tabAppend("<traits spec=\"TraitSet\" id='"+typeTraitID);
		writer.tabAppend("' traitname='Types' taxa=\"@"+taxaID+"\">");		
		// <taxon>=<deme>,
		// first taxon
		String[] splits = tipNames[0].split("_");
		String stateName = splits[splits.length-1];
		writer.tabAppend("\n"+tipNames[0]+"="+stateName);
		int ten=1;
		for(int i = 1; i < numTips; i++) {
			if (ten==10) {
				writer.tabAppend("\n");
				ten=0;
			}
			splits = tipNames[i].split("_");
			stateName = splits[splits.length-1];
			writer.tabAppend(","+tipNames[i]+"="+stateName);
			ten++;
		}	
		//</traits>
		writer.tabAppend("\n</traits>\n");
	}
	
	void writeLikelihood (XMLFileWriter writer) throws IOException {
		writeTree(writer);
		popModel.writeXML(writer,analysis);		
		writeLikelihoodODE(writer);		
		writer.tabAppend("\n<run spec='LikelihoodOut' stlikelihood='@"+stlhID+"' />");
	}
	
	void writeModelMCMC (XMLFileWriter writer) throws IOException {
		writeTree(writer);
		popModel.writeXML(writer,analysis);
		analysis.writeXML(writer);
	}
	
	void writeTree(XMLFileWriter writer) throws IOException {
		treeID = modelName+"-tree";
		// <tree spec="TreeParser" id="simpletree" adjustTipHeights="false" IsLabelledNewick="true">
		writer.tabAppend("<tree spec=\"TreeParser\" id=\""+treeID+"\" ");
		writer.tabAppend("adjustTipHeights=\"false\" IsLabelledNewick=\"true\">\n");
		// <input name="newick">
		writer.tabAppend(" <input name=\"newick\">\n");
		writer.tabAppend("  "+newick+"\n");
	    // </input>
		writer.tabAppend("  </input>\n");
		//  <trait idref="simpleDateTrait"/>
		if (createDateTrait) {
			writer.tabAppend("  <trait idref='"+dateTraitID+"'/>\n");
		}
		// if date trait
	    //<trait idref="simpleDateTrait"/>
		writer.tabAppend("</tree>\n");
	}

	// writes default likelihood object with simple/default parameter values
	public void writeLikelihoodODE(XMLFileWriter writer) throws IOException {
		stlhID = "stlh";
		String intervalsID = modelName+"-intervals";
		//<distribution spec="STreeLikelihoodODE" id="stlh"
		writer.tabAppend("<distribution spec=\"STreeLikelihoodODE\" id=\""+stlhID+"\"\n");
		//		popmodel='@twodeme'  
		writer.tabAppend(" popmodel='@"+modelName+"'");
		writer.tabAppend(" useStateName='true'");
		if (createTypeTrait) {
			writer.tabAppend(" typeTrait='@"+typeTraitID+"'");
		}
		writer.tabAppend(" equations='PL1' \n");
		writer.tabAppend("  forgiveY='true' stepSize='0.001' minP='.001'");
		//		useStateName='true' equations="PL2"
		//		forgiveAgtY='0.0' penaltyAgtY='0.0'			
		//		stepSize='0.001' 
		//		minP='.001'>
		writer.tabAppend(" >\n");
		//	   <treeIntervals spec="STreeIntervals" tree="@simpletree" id="simpleintervals"/>
		writer.tabAppend("  <treeIntervals spec=\"STreeIntervals\" tree=\"@"+treeID+"\" id=\""+intervalsID+"\"/>\n");
		//	</distribution>
		writer.tabAppend("</distribution>\n");
	}
	

	void writeTail(XMLFileWriter writer) throws IOException {
		 // <run spec="GeneralTest" stlikelihood="@simpledistribution" />
		
		writer.tabAppend("\n</beast>\n");
	}
	
	
	
}

