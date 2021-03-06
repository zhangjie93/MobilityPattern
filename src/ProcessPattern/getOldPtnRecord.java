package ProcessPattern;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import Config.Config;
import DBSCAN.Cluster;
import DBSCAN.ClusterAnalysis;
import DBSCAN.DataPoint;
import Model.OldPtnPoint;
import Model.OldPtnRecord;
import Model.StayPoint;
/*
 * author:youg
 * date:20160504
 * 从连续多天的StayRecord中提取每个用户的停留模式
 * 0raw:原始信令数据，不包含经纬度信息，按时间划分文件
 * 1fixed:添加经纬度信息，按id后两位划分文件，文件内按id和时间排序
 * 2timeSpan:记录每个ID每天最早出现的时间和位置以及最晚出现的时间和位置
 * 3timeLine：以15分钟为单位统计每个ID在每个时间段出现的次数
 * 4goodUser:数据质量好、用于下一步分析的用户ID列表。提取规则：7点前、19点后有记录，7-19点每3个小时有记录的用户数所占比例；用户比例：55%
 * 5goodRecord:4goodUser列表里的用户的完整记录，按id后两位分割到不同文件中
 * 7stayRecord:从5goodRecord中提取出的用户停留点记录
 * OldPtnRecord:从连续多天的7stayRecord中提取出的每个用户的停留模式
 */

@Deprecated
public class getOldPtnRecord {
	public static int total=0;
	public static int single=0;
	public static Map<String,List<DataPoint>> map;//存储（id,多日停留点序列）对
	@Deprecated
	public static List<OldPtnRecord> OldPtnRecords;//
	public static BufferedReader br;
	public static BufferedWriter bw;
	public static int dayLength;//可分析数据天数
	//载入停留点数据
	public static void importStayRecord(File goodRecordFile)throws Exception{
		System.out.println("Now importing "+goodRecordFile.getAbsolutePath());
		br = new BufferedReader(new FileReader(goodRecordFile));
		String af;
		String[] afs;
		while((af=br.readLine())!=null){
			if(af.charAt(af.length()-1)=='0')
				continue;
			afs = af.split(",");
			if(!map.containsKey(afs[0]))
				map.put(afs[0], new LinkedList<DataPoint>());
			String[] times = afs[2].split("-");
			StayPoint sp = new StayPoint(Double.valueOf(afs[3]),Double.valueOf(afs[4]),times[0],times[1],0,Integer.valueOf(afs[5]),0);
			DataPoint dp = new DataPoint(String.valueOf(map.get(afs[0]).size()),afs[0],afs[1],sp,false);
			map.get(afs[0]).add(dp);
		}
		br.close();
	}
	/*
	 * 对载入到map中的用户进行清洗，只保留每天都有数据的用户
	 */
	public static void shuffleStayRecord(){
		System.out.println("Now shuffling Stay Reocrd...");
		System.out.println("before shuffling, the number of user is: "+map.size());
		Set<String> idSet = new HashSet<String>(map.keySet());
		for(String id:idSet){
			List<DataPoint> dps = map.get(id);
			Set<String> dates = new HashSet<String>();
			for(DataPoint dp:dps)
				dates.add(dp.getDate());
			if(dates.size()<(dayLength/4*3))
				map.remove(id);
		}
		System.out.println("after shuffling, the number of user is: "+map.size());
	}
	/*
	 * 对集合内元素进行分析
	 * 该函数实现聚类主要策略
	 */
	public static OldPtnRecord generateOldPtnRecord(String id,List<Cluster> clusterList){
		OldPtnRecord pr = new OldPtnRecord(id);
		/*
		System.out.println("************************************");
		System.out.println(id);
		*/
		//对每一个类簇进行处理
		for(Cluster cls:clusterList){
			List<DataPoint> dps = cls.getDataPoints();
			if(dps==null || dps.size()==0)
				continue;
			/*
			System.out.println("-----------------------------");
			for(DataPoint dp:dps){
				System.out.println(dp.getDate()+","+dp.getSTime()+","+dp.getETime());
			}
			*/
			//聚类集合只有一个点的情况
			total+=1;
			if(dps.size()==1){
				DataPoint dp = dps.get(0);
				OldPtnPoint pp = new OldPtnPoint(dp.getLon(),dp.getLat(),0,1,0.0);
				pp.getSTimes().add(dp.getSTime());
				pp.getETimes().add(dp.getETime());
				pr.getDynamicPoints().add(pp);
				single+=1;
				continue;
			}
			//聚类集合有多个点的情况
			OldPtnPoint pp = new OldPtnPoint(0.0,0.0,0,dps.size(),0.0);
			double mLon=0.0;
			double mLat=0.0;
			int[] timeTag=new int[288];
			boolean[] timeBool = new boolean[288];
			for(DataPoint dp:dps){
				mLon+=dp.getLon();
				mLat+=dp.getLat();
				int sTag = (Integer.valueOf(dp.getSTime().substring(0,2))*60+Integer.valueOf(dp.getSTime().substring(2,4)))/5;
				int eTag = (Integer.valueOf(dp.getETime().substring(0,2))*60+Integer.valueOf(dp.getETime().substring(2,4)))/5;
				for(int i=sTag;i<=eTag;i++)
					timeTag[i]+=1;
			}
			mLon/=dps.size();
			mLat/=dps.size();
			pp.setLon(mLon);
			pp.setLat(mLat);
			//时间聚合标准
			for(int i=0;i<timeTag.length;i++){
				timeBool[i]=false;
				if(timeTag[i]>dayLength/3)
					timeBool[i]=true;
			}
			int i=0;
			while(i<timeBool.length && !timeBool[i])
				i+=1;
			if(i==timeBool.length)
				continue;
			while(i<timeBool.length){
				if(i==0 || timeBool[i-1]!=timeBool[i]){
					if(timeBool[i]){
						//起点时间
						String st = String.format("%02d",i*5/60)+String.format("%02d",i*5%60)+"00";
						pp.getSTimes().add(st);
					}else{
						//终点时间
						String et = String.format("%02d",i*5/60)+String.format("%02d",i*5%60)+"00";
						pp.getETimes().add(et);
					}
				}
				i+=1;
			}
			//最后一个点是true的情况，需要增加一个终点时间
			if(timeBool[i-1]){
				String et = String.format("%02d",i*5/60)+String.format("%02d",i*5%60)+"00";
				pp.getETimes().add(et);
			}
			pr.getNormalPoints().add(pp);
		}//endfor
		return pr;
	}
	//识别停留点属性
	public static void identifyOldPtnRecord(OldPtnRecord OldPtnRecord){
		
	}
	//输出停留模式
	public static void exportOldPtnRecord(File stayRecordFile)throws Exception{
		System.out.println("Now exporting "+stayRecordFile.getAbsolutePath());
		int[] normalPPL = new int[6];
		for(OldPtnRecord pr:OldPtnRecords){
			int m = pr.getNormalPoints().size();
			if(m!=1)
				continue;
			int n = pr.getDynamicPoints().size();
			if(n!=0)
				continue;
			System.out.println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&");
			System.out.println(pr.getId());
			System.out.println("-----------------");
			OldPtnPoint pp = pr.getNormalPoints().get(0);
			for(int i=0;i<pp.getSTimes().size();i++)
				System.out.println(pp.getSTimes().get(i)+"-"+pp.getETimes().get(i));
		}
		for(int i=0;i<=5;i++)
			System.out.println(i+":"+normalPPL[i]);
		//for(OldPtnRecord pr:OldPtnRecords)
		//	System.out.println(pr.toString());
	}
	public static void print(String id,List<Cluster> clusterList){
		for(Cluster c:clusterList){
			for(DataPoint dp:c.getDataPoints()){
				//if(!dp.getId().equals("3700204652417615400"))
				//	continue;
				System.out.println(id+","+c.getClusterName()+","+dp.getLon()+","+dp.getLat()+","+dp.getDate()+","+dp.getSTime()+","+dp.getETime());
			}
		}
	}
	public static void printPR(OldPtnRecord pr){
		if(!pr.getId().equals("3699470638537988000"))
			return;
		System.out.println(pr.getId()+"--pr------------");
		System.out.println(pr.getNormalPoints().size());
		System.out.println(pr.getDynamicPoints().size());
	}
	public static void main(String[] args)throws Exception{
		Config.init();
		File workPath = new File(Config.getAttr(Config.WorkPath));
		File[] workPathPerday = workPath.listFiles();
		File[] stayRecordPathPerday = new File[workPathPerday.length];
		dayLength = workPathPerday.length;
		for(int i=0;i<workPathPerday.length;i++){
			stayRecordPathPerday[i] = new File(workPathPerday[i].getAbsolutePath()+File.separator+Config.StayRecordPath);
			//System.out.println(stayRecordPathPerday[i].getAbsolutePath());
		}
		if(stayRecordPathPerday.length<=0){
			System.out.println("no input files,please check the path config.");
			System.out.println("finish");
			return;
		}
		File[] stayRecordFiles = stayRecordPathPerday[0].listFiles();
		//一个处理周期，包括stayRecord文件夹下的一个文件的连续多天
		for(File stayRecordFile:stayRecordFiles){
			File[] stayRecordFilePerday = new File[stayRecordPathPerday.length];
			for(int i=0;i<stayRecordPathPerday.length;i++){
				stayRecordFilePerday[i] = new File(stayRecordPathPerday[i]+File.separator+stayRecordFile.getName());
				//System.out.println(stayRecordFilePerday[i].getAbsolutePath());
			}
			map = new HashMap<String,List<DataPoint>>();
			OldPtnRecords = new LinkedList<OldPtnRecord>();
			for(File file:stayRecordFilePerday){
				importStayRecord(file);
			}
			shuffleStayRecord();
			System.out.println("Now generateOldPtnRecord "+stayRecordFile.getName());
			for(String id:map.keySet()){
				ClusterAnalysis ca = new ClusterAnalysis();
				List<Cluster> clusterList = ca.doDbscanAnalysis(map.get(id), 500, 1);
				//print(id,clusterList);
				//if(true)
				//	continue;
				OldPtnRecord pr = generateOldPtnRecord(id,clusterList);
				//if(pr.getNormalPoints().size()==1)
				//	print(id,clusterList);
				//printPR(pr);
				identifyOldPtnRecord(pr);
				OldPtnRecords.add(pr);
			}//endfor
			exportOldPtnRecord(stayRecordFile);
			//System.out.println("total="+total+";single="+single+"\n");
			break;
		}//endfor
	}
}
