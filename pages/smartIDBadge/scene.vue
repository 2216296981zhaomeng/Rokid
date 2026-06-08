<template>
	<view class="scene-page">
		<u-navbar title="场景运行中" title-bold :border-bottom="false" :background="{background: '#ffffff'}"
			back-icon-color="#202733" title-color="#202733" />

		<view class="status-card" :class="statusClass">
			<view class="status-top">
				<view class="scene-title">
					<text class="title">{{ sceneNameText }}</text>
					<text class="sub">{{ businessLabel }}：{{ businessDisplayText }}</text>
				</view>
				<view class="status-pill">
					<text class="status-dot"></text>
					{{ statusText }}
				</view>
			</view>
			<view class="runtime-row">
				<view class="timer">
					<text class="time">{{ durationText }}</text>
					<text class="time-label">{{ durationLabel }}</text>
				</view>
				<view class="runtime-meta">
					<text>{{ sourceDeviceText }}</text>
					<text>{{ realtimeStatusText }}</text>
					<text v-if="transcriptCount">已同步 {{ transcriptCount }} 段转写</text>
				</view>
			</view>
			<view class="warning-line" v-if="showRealtimeWarning">
				实时连接中断，后台仍在分析，页面已切换为轮询同步。
			</view>
			<view class="warning-line error" v-if="isFailed && latestSnapshotError">
				{{ latestSnapshotError }}
			</view>
			<view class="warning-line" v-if="isAiGlassesMode && lastRokidEventName">
				眼镜原生事件：{{ lastRokidEventName }}{{ lastRokidEventDetail ? ' / ' + lastRokidEventDetail : '' }}
			</view>
		</view>

		<scroll-view scroll-y class="content-scroll">
			<view class="ai-live-card">
				<view class="section-head">
					<view>
						<text class="section-title">{{ aiFeedbackTitle }}</text>
						<text class="section-sub">{{ aiFeedbackSubText }}</text>
					</view>
					<text class="refresh" @click="loadStatus">刷新</text>
				</view>

				<view class="empty-block" v-if="!hasAnalysis">
					<text class="empty-title">{{ aiFeedbackEmptyTitle }}</text>
					<text class="empty-sub">{{ aiFeedbackEmptySubText }}</text>
				</view>

				<view class="script-block" v-if="topScripts.length">
					<view class="block-title">推荐话术</view>
					<view class="script-item" v-for="(item,index) in topScripts" :key="'script-'+index">
						<text class="script-text">{{ item }}</text>
						<text class="copy-btn" @click="copyText(item)">复制</text>
					</view>
				</view>

				<view class="quick-grid" v-if="nextActionItems.length || resistanceItems.length">
					<view class="quick-box action" v-if="nextActionItems.length">
						<view class="quick-title">下一步动作</view>
						<view class="quick-line" v-for="(item,index) in nextActionItems" :key="'action-'+index">
							{{ item }}
						</view>
					</view>
					<view class="quick-box resistance" v-if="resistanceItems.length">
						<view class="quick-title">{{ resistanceTitle }}</view>
						<view class="quick-line" v-for="(item,index) in resistanceItems" :key="'resistance-'+index">
							{{ item }}
						</view>
					</view>
				</view>
			</view>

			<view class="section-card" v-if="scoreItems.length || sceneChips.length || extractedRows.length">
				<view class="section-head compact">
					<view>
						<text class="section-title">关键判断</text>
						<text class="section-sub">{{ judgmentSubText }}</text>
					</view>
				</view>
				<view class="score-grid" v-if="scoreItems.length">
					<view class="score-item" v-for="item in scoreItems" :key="item.key">
						<view class="score-top">
							<text>{{ item.label }}</text>
							<text class="score-num">{{ item.valueText }}</text>
						</view>
						<view class="score-bar">
							<view class="score-fill" :style="{width: item.percent + '%'}"></view>
						</view>
					</view>
				</view>
				<view class="scene-chip-list" v-if="sceneChips.length">
					<view class="scene-chip" v-for="item in sceneChips" :key="item.sceneCode || item.sceneName">
						<text>{{ item.sceneName || item.sceneCode }}</text>
						<text class="confidence">{{ item.confidence || 0 }}%</text>
					</view>
				</view>
				<view class="info-list" v-if="extractedRows.length">
					<view class="info-row" v-for="item in extractedRows" :key="item.key">
						<text class="info-label">{{ item.label }}</text>
						<text class="info-value">{{ item.value }}</text>
					</view>
				</view>
			</view>

			<view class="section-card" v-if="summaryText">
				<view class="section-head compact">
					<view>
						<text class="section-title">阶段摘要</text>
						<text class="section-sub">AI 根据当前已分析内容生成</text>
					</view>
				</view>
				<text class="summary-text">{{ summaryText }}</text>
			</view>

			<view class="section-card transcript-card">
				<view class="section-head compact">
					<view>
						<text class="section-title">实时转写</text>
						<text class="section-sub">最近 {{ recentTranscriptList.length }} 条，完整内容可展开查看</text>
					</view>
					<text class="refresh" @click="showTranscriptPanel = true">查看全部</text>
				</view>
				<view class="live-transcript" v-if="currentPartialTranscript">
					<view class="live-dot"></view>
					<view class="live-content">
						<text class="live-label">正在识别</text>
						<text class="live-text">{{ currentPartialTranscript.text }}</text>
					</view>
				</view>
				<view class="empty-block small" v-if="!recentTranscriptList.length && !currentPartialTranscript">
					<text class="empty-sub">等待转写内容同步...</text>
				</view>
				<view class="transcript-list" v-if="recentTranscriptList.length">
					<view class="transcript-item" v-for="item in recentTranscriptList" :key="item.key">
						<view class="transcript-meta">
							<text>{{ item.segmentSeq ? '片段 ' + item.segmentSeq : item.final ? '稳定句子' : '临时识别' }}</text>
							<text>{{ item.timeText }}</text>
						</view>
						<text class="transcript-text">{{ item.text }}</text>
					</view>
				</view>
			</view>

			<view class="bottom-space"></view>
		</scroll-view>

		<view class="footer-bar">
			<view class="footer-hint">
				{{ footerHintText }}
			</view>
			<view class="footer-btn danger" v-if="canEndScene" :class="{disabled: finishing}" @click="endScene">
				{{ endButtonText }}
			</view>
			<view class="footer-actions" v-else-if="canViewReport">
				<view class="footer-btn ghost compact" v-if="canSyncFields" @click="openSyncConfirm">
					字段同步
				</view>
				<view class="footer-btn primary compact" @click="viewReport">
					查看报告
				</view>
			</view>
			<view class="footer-btn ghost" v-else @click="loadStatus">
				刷新状态
			</view>
		</view>

		<view class="transcript-mask" v-if="showTranscriptPanel" @click="showTranscriptPanel = false">
			<view class="transcript-panel" @click.stop>
				<view class="transcript-panel-head">
					<text>完整转写</text>
					<text class="close" @click="showTranscriptPanel = false">关闭</text>
				</view>
				<scroll-view scroll-y class="transcript-panel-scroll">
					<view class="live-transcript panel-live" v-if="currentPartialTranscript">
						<view class="live-dot"></view>
						<view class="live-content">
							<text class="live-label">正在识别</text>
							<text class="live-text">{{ currentPartialTranscript.text }}</text>
						</view>
					</view>
					<view class="empty-block small" v-if="!transcriptPanelList.length && !currentPartialTranscript">
						<text class="empty-sub">暂无转写内容</text>
					</view>
					<view class="transcript-item panel-item" v-for="item in transcriptPanelList" :key="'all-'+item.key">
						<view class="transcript-meta">
							<text>{{ item.segmentSeq ? '片段 ' + item.segmentSeq : item.final ? '稳定句子' : '临时识别' }}</text>
							<text>{{ item.timeText }}</text>
						</view>
						<text class="transcript-text">{{ item.text }}</text>
					</view>
				</scroll-view>
			</view>
		</view>
	</view>
</template>

<script>
	import rokidGlass from '@/utils/rokidGlass.js'

	const STATUS_RUNNING = 1;
	const STATUS_FINISHING = 2;
	const STATUS_COMPLETED = 3;
	const STATUS_FAILED = 4;

	const FIELD_LABELS = {
		ownerPersonality: '业主性格',
		motivation: '出售动机',
		propertyFeatures: '房源卖点',
		priceExpectation: '价格预期',
		bottomPrice: '底价空间',
		agencyFeeConcern: '代理费顾虑',
		priceReductionConcern: '降价顾虑',
		marketAttitude: '市场态度',
		trustBuilding: '信任建立',
		timeConstraint: '时间节奏',
		sellingReason: '卖房原因',
		showingWillingness: '看房意愿',
		agentStrategy: '经纪人策略',
		priceDisplayPreference: '价格展示偏好',
		customerProfile: '客户画像',
		customerNeeds: '客户需求',
		budget: '预算信息',
		paymentAbility: '付款能力',
		propertyPreference: '房源偏好',
		decisionMaker: '决策人',
		meetingTopic: '会议主题',
		meeting_topic: '会议主题',
		decisions: '关键决策',
		actionItems: '待办事项',
		action_items: '待办事项',
		owners: '责任人',
		assignee: '责任人',
		assignees: '责任人',
		deadlines: '截止时间',
		dueDate: '截止时间',
		due_date: '截止时间',
		blockers: '问题阻塞',
		teamMood: '团队状态',
		team_mood: '团队状态',
		reviewConclusion: '复盘结论',
		review_conclusion: '复盘结论',
		meetingType: '会议类型',
		meeting_type: '会议类型',
		meetingName: '会议名称',
		meeting_name: '会议名称',
		meetingAgenda: '会议议程',
		meeting_agenda: '会议议程',
		meetingEffectiveness: '会议有效性',
		meeting_effectiveness: '会议有效性',
		meetingQuality: '会议质量',
		meeting_quality: '会议质量',
		meetingEfficiency: '会议效率',
		meeting_efficiency: '会议效率',
		executionClarity: '执行清晰度',
		execution_clarity: '执行清晰度',
		actionClarity: '行动清晰度',
		action_clarity: '行动清晰度',
		decisionClarity: '决策清晰度',
		decision_clarity: '决策清晰度',
		goalClarity: '目标清晰度',
		goal_clarity: '目标清晰度',
		riskLevel: '问题风险',
		risk_level: '问题风险',
		problemRisk: '问题风险',
		problem_risk: '问题风险',
		teamStatus: '团队状态',
		team_status: '团队状态',
		teamEmotion: '团队情绪',
		team_emotion: '团队情绪',
		followUpClarity: '跟进清晰度',
		follow_up_clarity: '跟进清晰度',
		keyDecisions: '关键决策',
		key_decisions: '关键决策',
		decisionItems: '决策事项',
		decision_items: '决策事项',
		todoItems: '待办事项',
		todo_items: '待办事项',
		tasks: '任务事项',
		responsiblePerson: '责任人',
		responsible_person: '责任人',
		deadline: '截止时间',
		problems: '问题阻塞',
		issues: '问题阻塞',
		risks: '风险问题',
		performanceTarget: '业绩目标',
		performance_target: '业绩目标',
		targetBreakdown: '目标拆解',
		target_breakdown: '目标拆解',
		resourceOpportunities: '房客源机会',
		resource_opportunities: '房客源机会',
		trainingNeeds: '培训需求',
		training_needs: '培训需求',
		newcomerTraining: '新人带教',
		newcomer_training: '新人带教',
		coachingSuggestions: '辅导建议',
		coaching_suggestions: '辅导建议',
		managementSuggestions: '管理建议',
		management_suggestions: '管理建议',
		meetingAtmosphere: '会议氛围',
		meeting_atmosphere: '会议氛围',
		teamEngagement: '团队参与度',
		team_engagement: '团队参与度',
		resourceFairness: '资源公平感',
		resource_fairness: '资源公平感',
		issueClarity: '问题清晰度',
		issue_clarity: '问题清晰度',
		resourceAllocationPrinciple: '资源分配原则',
		resource_allocation_principle: '资源分配原则',
		exampleCases: '案例说明',
		example_cases: '案例说明',
		keyMessage: '核心信息',
		key_message: '核心信息',
		coreMessage: '核心信息',
		core_message: '核心信息',
		managementIssue: '管理问题',
		management_issue: '管理问题',
		employeeFeedback: '成员反馈',
		employee_feedback: '成员反馈',
		values: '价值观',
		culture: '团队文化',
		rolePlay: '角色演练',
		role_play: '角色演练',
		needs: '需求信息',
		promotionSummary: '推广摘要',
		promotedHouses: '推广房源',
		promotionHouses: '推广房源',
		houseFeedback: '房源反馈',
		channelFeedback: '渠道反馈',
		storeFeedback: '门店反馈',
		cooperationIntention: '合作意向',
		cooperation_intention: '合作意向',
		customerLeads: '客户线索',
		customer_leads: '客户线索',
		promotionObjections: '推广异议',
		promotion_objections: '推广异议',
		followUpCoordination: '协同动作',
		follow_up_coordination: '协同动作',
		promotionClarity: '推广清晰度',
		channelInterest: '渠道兴趣',
		leadPotential: '线索潜力',
		cooperationPotential: '合作潜力',
		pricingAlignment: '价格预期一致度',
		pricing_alignment: '价格预期一致度',
		priceClarity: '价格清晰度',
		price_clarity: '价格清晰度',
		pricingClarity: '价格清晰度',
		pricing_clarity: '价格清晰度',
		ownerProfileClarity: '业主情况清晰度',
		owner_profile_clarity: '业主情况清晰度',
		ownerSituation: '业主情况',
		owner_situation: '业主情况',
		ownerMotivation: '业主卖房动机',
		owner_motivation: '业主卖房动机',
		singleAgentValueRecognition: '单边代理价值认同',
		single_agent_value_recognition: '单边代理价值认同',
		mlsPromotionUnderstanding: '全城联卖理解度',
		mls_promotion_understanding: '全城联卖理解度',
		objectionHandling: '异议处理',
		objection_handling: '异议处理',
		objectionSeverity: '抗性强度',
		objection_severity: '抗性强度',
		resistanceAnalysis: '抗性识别',
		resistance_analysis: '抗性识别',
		objectionAnalysis: '疑虑识别',
		objection_analysis: '疑虑识别',
		serviceValueRecognition: '服务价值认同',
		service_value_recognition: '服务价值认同',
		inspectionAcceptance: '验房接受度',
		inspection_acceptance: '验房接受度',
		commissionConcern: '佣金顾虑',
		commission_concern: '佣金顾虑',
		inspectionReadiness: '验房推进度',
		inspection_readiness: '验房推进度',
		readinessForInspection: '验房准备度',
		readiness_for_inspection: '验房准备度',
		closingReadiness: '成交准备度',
		closing_readiness: '成交准备度',
		readinessToSign: '签约准备度',
		readiness_to_sign: '签约准备度',
		valueCommunication: '价值传递',
		value_communication: '价值传递',
		priceNegotiation: '价格沟通',
		price_negotiation: '价格沟通',
		agentComment: '经纪人判断',
		agent_comment: '经纪人判断',
		ownerQuote: '业主报价',
		owner_quote: '业主报价',
		concessionSignal: '让价信号',
		concession_signal: '让价信号',
		listingStatus: '挂牌状态',
		listing_status: '挂牌状态',
		currentListing: '当前挂牌情况',
		current_listing: '当前挂牌情况',
		otherAgents: '其他中介情况',
		other_agents: '其他中介情况',
		propertyBasicInfo: '房源基础信息',
		property_basic_info: '房源基础信息',
		taxInfo: '税费信息',
		tax_info: '税费信息',
		ownerBackground: '业主背景',
		owner_background: '业主背景',
		housingCondition: '房屋状态',
		housing_condition: '房屋状态',
		viewingArrangement: '看房安排',
		viewing_arrangement: '看房安排',
		priorMemory: '历史沉淀',
		prior_memory: '历史沉淀',
		uncertainties: '待确认信息',
		businessObject: '业务对象',
		business_object: '业务对象',
		selectedScenes: '已选场景',
		selected_scenes: '已选场景',
		layout: '户型',
		area: '面积',
		parking: '车位',
		education: '学位',
		occupancy: '居住状态',
		communicationPreference: '沟通偏好',
		communication_preference: '沟通偏好',
		decisionChain: '决策链',
		decision_chain: '决策链',
		trustLevel: '信任程度',
		trust_level: '信任程度',
		urgencyLevel: '紧迫程度',
		urgency_level: '紧迫程度',
		community: '小区',
		content: '内容',
		type: '类型',
		priority: '优先级',
		description: '说明',
		intensity: '强度',
		level: '强度',
		followUpStrategy: '跟进策略',
		follow_up_strategy: '跟进策略',
		strategy: '策略',
		category: '分类',
		resistance: '抗性/阻力',
		strength: '强度'
	};

	const FIELD_LABEL_ALIASES = {
		pricingalignment: '价格预期一致度',
		pricealignment: '价格预期一致度',
		priceclarity: '价格清晰度',
		pricingclarity: '价格清晰度',
		ownerprofileclarity: '业主情况清晰度',
		ownerclarity: '业主情况清晰度',
		ownerprofile: '业主情况识别',
		ownersituation: '业主情况',
		ownermotivation: '业主卖房动机',
		singleagentvaluerecognition: '单边代理价值认同',
		singleagencyvaluerecognition: '单边代理价值认同',
		singleagentvalue: '单边代理价值认同',
		singleagencyvalue: '单边代理价值认同',
		mlspromotionunderstanding: '全城联卖理解度',
		citywidepromotionunderstanding: '全城联卖理解度',
		promotionunderstanding: '推广理解度',
		objectionhandling: '异议处理',
		objectionseverity: '抗性强度',
		inspectionreadiness: '验房推进度',
		readinessforinspection: '验房准备度',
		closingreadiness: '成交准备度',
		readinesstosign: '签约准备度',
		valuerecommunication: '价值传递',
		valuecommunication: '价值传递',
		pricenegotiation: '价格沟通',
		agentcomment: '经纪人判断',
		ownerquote: '业主报价',
		concessionsignal: '让价信号',
		listingstatus: '挂牌状态',
		currentlisting: '当前挂牌情况',
		otheragents: '其他中介情况',
		propertybasicinfo: '房源基础信息',
		taxinfo: '税费信息',
		ownerbackground: '业主背景',
		housingcondition: '房屋状态',
		viewingarrangement: '看房安排',
		priormemory: '历史沉淀',
		uncertainties: '待确认信息',
		businessobject: '业务对象',
		selectedscenes: '已选场景',
		communicationpreference: '沟通偏好',
		decisionchain: '决策链',
		trustlevel: '信任程度',
		urgencylevel: '紧迫程度',
		trustbuilding: '信任建立',
		trustfoundation: '信任基础',
		resistanceanalysis: '抗性识别',
		objectionanalysis: '疑虑识别',
		objectionresolution: '疑虑化解度',
		valueacceptance: '价值接受度',
		valuerecognition: '价值认知',
		servicevaluerecognition: '服务价值认同',
		priceacceptance: '价格接受度',
		urgency: '紧迫度',
		intention: '成交意向',
		motivation: '动机原因',
		intensity: '强度',
		description: '说明',
		followupstrategy: '跟进策略',
		followstrategy: '跟进策略'
	};

	const LANDLORD_SCORE_META = [{
		key: 'intention',
		label: '成交意向'
	}, {
		key: 'trust',
		label: '信任基础'
	}, {
		key: 'urgency',
		label: '出售紧迫度'
	}, {
		key: 'valueRecognition',
		label: '价值认知'
	}];

	const CUSTOMER_SCORE_META = [{
		key: 'intention',
		label: '成交意向'
	}, {
		key: 'trust',
		label: '信任基础'
	}, {
		key: 'priceAcceptance',
		label: '价格接受度'
	}, {
		key: 'objectionResolution',
		label: '疑虑化解度'
	}, {
		key: 'urgency',
		label: '需求紧迫度'
	}, {
		key: 'showingFeedback',
		label: '带看反馈'
	}];

	const MEETING_SCORE_META = [{
		key: 'meetingEffectiveness',
		label: '会议有效性'
	}, {
		key: 'executionClarity',
		label: '执行清晰度'
	}, {
		key: 'riskLevel',
		label: '问题风险'
	}, {
		key: 'teamStatus',
		label: '团队状态'
	}, {
		key: 'followUpClarity',
		label: '跟进清晰度'
	}];

	const CHANNEL_SCORE_META = [{
		key: 'promotionClarity',
		label: '推广清晰度'
	}, {
		key: 'channelInterest',
		label: '渠道兴趣'
	}, {
		key: 'leadPotential',
		label: '线索潜力'
	}, {
		key: 'cooperationPotential',
		label: '合作潜力'
	}, {
		key: 'followUpClarity',
		label: '跟进清晰度'
	}];

	export default {
		data() {
			return {
				sessionId: '',
				deviceNo: '',
				deviceType: '',
				deviceName: '',
				glassesPreparedFromRoute: false,
				recordId: '',
				sceneCategory: 1,
				sceneNames: '',
				selectedSceneCodes: '',
				businessId: '',
				businessName: '',
				businessType: 'NONE',
				businessObjects: [],
				businessObjectsText: '',
				status: STATUS_RUNNING,
				reportReady: false,
				wsConnected: false,
				wsState: 'idle',
				reconnectCount: 0,
				reconnectTimer: null,
				socketTask: null,
				timer: null,
				pollTimer: null,
				pageClosed: false,
				finishing: false,
				showTranscriptPanel: false,
				analysis: {},
				transcriptList: [],
				transcriptCount: 0,
				maxSegmentSeq: 0,
				startedAtMs: 0,
				endedAtMs: 0,
				serverOffsetMs: 0,
				displayDurationSeconds: 0,
				latestSnapshotError: '',
				unbindRokidEvent: null,
				glassesState: {
					ready: false,
					sceneReady: false,
					audioStarted: false,
					glassId: '',
					glassIdSource: '',
					glassIdStable: false,
					deviceId: '',
					sn: '',
					deviceName: ''
				},
				glassesSocketTask: null,
				glassesSocketOpened: false,
				glassesSocketState: 'idle',
				glassesSocketUrl: '',
				glassesReconnectCount: 0,
				glassesReconnectTimer: null,
				manualClosingGlassesSocket: false,
				glassesAudioPath: '',
				glassesAudioUrl: '',
				teleprompterRunning: false,
				glassesAudioStartConfirmed: false,
				glassesAudioError: '',
				teleprompterText: '',
				lastTeleprompterScriptsText: '',
				glassesRuntimeStopping: false,
				glassesAudioSequence: 0,
				glassesAudioBytes: 0,
				glassesAudioSendQueue: [],
				glassesPcmPendingBytes: [],
				glassesPcmPendingSize: 0,
				glassesAudioUploadKey: '',
				glassesAudioChunkIndex: 0,
				glassesLastSocketSentAt: 0,
				glassesSocketSending: false,
				glassesSocketSentChunks: 0,
				glassesSocketSentBytes: 0,
				glassesSocketSendFailed: 0,
				glassesDroppedChunks: 0,
				glassesSkippedSilentChunks: 0,
				glassesAudioCodecType: null,
				glassesSilentChunks: 0,
				glassesVoiceRun: 0,
				glassesVoiceSilenceRun: 0,
				glassesVoiceActive: false,
				glassesVoiceCandidateQueue: [],
				lastGlassesAudioLevel: {},
				lastGlassesAudioGain: 1,
				glassesNativeUpload: false,
				lastRokidEventName: '',
				lastRokidEventDetail: '',
				localAiGlassesStartFailed: false,
				glassesStartupPhase: '',
				glassesCustomViewOpenedAtMs: 0
			}
		},
		computed: {
			statusCode() {
				if (this.status === 'RUNNING') return STATUS_RUNNING;
				if (this.status === 'FINISHING') return STATUS_FINISHING;
				if (this.status === 'COMPLETED') return STATUS_COMPLETED;
				if (this.status === 'FAILED') return STATUS_FAILED;
				return Number(this.status || STATUS_RUNNING);
			},
			isRunning() {
				return this.statusCode === STATUS_RUNNING;
			},
			isFinishing() {
				return this.statusCode === STATUS_FINISHING;
			},
			isCompleted() {
				return this.statusCode === STATUS_COMPLETED;
			},
			isFailed() {
				return this.statusCode === STATUS_FAILED;
			},
			isUnclaimedMode() {
				return this.deviceNo === 'UNCLAIMED_RECORDING';
			},
			isAiGlassesMode() {
				return this.deviceType === 'AI_GLASSES';
			},
			statusClass() {
				if (this.isCompleted) return 'completed';
				if (this.isFinishing) return 'finishing';
				if (this.isFailed) return 'failed';
				return this.isUnclaimedMode ? 'unclaimed' : 'running';
			},
			sceneNameText() {
				if (this.sceneNames) return this.sceneNames;
				if (this.sceneCategory == 1) return '房东沟通场景';
				if (this.sceneCategory == 2) return '客户沟通场景';
				if (this.sceneCategory == 4) return '渠道推广';
				return '会议场景';
			},
			businessLabel() {
				if (this.businessType === 'HOUSE' || this.sceneCategory == 1) return '房源';
				if (this.businessType === 'CUSTOMER' || this.sceneCategory == 2) return '客源';
				if (this.sceneCategory == 3) return '会议';
				if (this.sceneCategory == 4) return '推广房源';
				return '对象';
			},
			businessDisplayText() {
				if (this.sceneCategory == 4 && this.businessObjects.length) {
					const first = this.businessObjects[0].businessDisplayName || this.businessObjects[0].businessName ||
						this.businessName || '推广房源';
					return `${first}${this.businessObjects.length > 1 ? ' 等' + this.businessObjects.length + '套' : ''}`;
				}
				if (this.businessName) return this.businessName;
				return this.sceneCategory == 3 ? '未绑定业务对象' : '暂未绑定';
			},
			statusText() {
				if (this.isAiGlassesMode && this.glassesStartupPhase && !this.isFailed && !this.isCompleted && !this
					.isFinishing) {
					const phaseMap = {
						preparing: '眼镜准备中',
						creatingSession: '创建AI会话中',
						connectingSocket: '连接音频通道中',
						startingAudio: '启动眼镜录音中',
						running: '录音与AI分析中'
					};
					return phaseMap[this.glassesStartupPhase] || '眼镜准备中';
				}
				if (this.isCompleted) return '分析完成';
				if (this.isFailed) return '分析异常';
				if (this.isFinishing) return '报告生成中';
				if (this.isUnclaimedMode) return '补分析中';
				return '录音与AI分析中';
			},
			durationLabel() {
				if (this.isCompleted) return '最终时长';
				if (this.isFinishing) return '录音已结束';
				if (this.isUnclaimedMode) return '补分析耗时';
				return '录音时长';
			},
			durationText() {
				const seconds = Math.max(0, Number(this.displayDurationSeconds || 0));
				const h = Math.floor(seconds / 3600);
				const m = Math.floor((seconds % 3600) / 60);
				const s = Math.floor(seconds % 60);
				if (h > 0) {
					return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
				}
				return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
			},
			sourceDeviceText() {
				if (this.isUnclaimedMode) return '来源：未归档录音';
				if (this.isAiGlassesMode)
					return `设备：AI眼镜${this.deviceName || this.deviceNo ? ' · ' + (this.deviceName || this.deviceNo) : ''}`;
				return `设备：智能工牌${this.deviceNo ? ' · ' + this.deviceNo : ''}`;
			},
			realtimeStatusText() {
				if (this.isCompleted) return '报告已生成';
				if (this.isFailed) return '需要处理异常';
				if (this.isFinishing) return '后台生成报告中';
				if (this.isAiGlassesMode) {
					if (this.glassesStartupPhase && this.glassesStartupPhase !== 'running') {
						const phaseMap = {
							preparing: '正在打开眼镜提词场景',
							creatingSession: '眼镜已准备，正在创建AI会话',
							connectingSocket: 'AI会话已创建，正在连接后端音频通道',
							startingAudio: '后端音频通道已连接，正在启动眼镜录音'
						};
						return phaseMap[this.glassesStartupPhase] || '眼镜准备中';
					}
					const aiWsText = this.wsConnected ? 'AI长连接正常' : (this.wsState === 'fallback' ? 'AI轮询同步中' :
						(this.wsState === 'reconnecting' ? 'AI长连接重连中' : 'AI长连接连接中'));
					const audioMap = {
						connected: '眼镜音频已连接',
						connecting: '眼镜音频连接中',
						reconnecting: '眼镜音频重连中',
						error: '眼镜音频异常',
						failed: '眼镜音频失败',
						timeout: '眼镜音频超时',
						closed: '眼镜音频已关闭'
					};
					return `${aiWsText} / ${audioMap[this.glassesSocketState] || '眼镜音频待连接'}`;
				}
				if (this.wsConnected) return '实时连接正常';
				if (this.wsState === 'fallback') return '轮询同步中';
				if (this.wsState === 'reconnecting') return '正在重连';
				return '连接中';
			},
			showRealtimeWarning() {
				if (this.isCompleted || this.isFailed) return false;
				if (this.isAiGlassesMode && ['error', 'failed', 'timeout'].includes(this.glassesSocketState)) return true;
				return !this.wsConnected && this.wsState === 'fallback';
			},
			hasAnalysis() {
				if (this.isWarmupGenerating) return false;
				return !!(this.summaryText || this.topScripts.length || this.nextActionItems.length || this.resistanceItems
					.length ||
					this.scoreItems.length || this.sceneChips.length || this.extractedRows.length);
			},
			isWarmupAnalysis() {
				const stage = String(this.analysis.analysisStage || '').toUpperCase();
				return stage === 'WARMUP' || this.analysis.warmup === true;
			},
			isWarmupGenerating() {
				return this.isWarmupAnalysis && this.analysis.warmupGenerating !== false;
			},
			aiFeedbackTitle() {
				return this.isWarmupAnalysis ? '沟通预案' : 'AI即时反馈';
			},
			aiFeedbackSubText() {
				if (this.isWarmupAnalysis) {
					return '基于业务对象、选择场景和历史沉淀生成，真实对话开始后会自动刷新';
				}
				return '优先展示沟通现场最值得马上使用的信息';
			},
			aiFeedbackEmptyTitle() {
				return this.isWarmupGenerating ? 'DeepSeek正在生成沟通预案' : (this.isWarmupAnalysis ? '正在完善沟通预案' : '等待有效沟通内容');
			},
			aiFeedbackEmptySubText() {
				if (this.isWarmupGenerating) {
					return '正在结合业务对象、选择场景和历史沉淀生成专属建议。';
				}
				if (this.isWarmupAnalysis) {
					return '请先围绕当前对象确认关键信息，AI 会随沟通内容更新反馈。';
				}
				return 'AI 会在识别到稳定转写后生成话术、抗性和下一步动作。';
			},
			summaryText() {
				return this.analysis.summary || this.analysis.communicationSummary || '';
			},
			topScripts() {
				return this.realtimeScriptList(this.recommendedScriptSource(), 4);
			},
			teleprompterScripts() {
				return this.realtimeList(this.recommendedScriptSource(), 2);
			},
			nextActionItems() {
				return this.realtimeList(this.analysis.nextActions || this.analysis.actions, 4);
			},
			resistanceItems() {
				return this.realtimeList(this.analysis.resistances || this.analysis.objections || this.analysis.blockers,
					4);
			},
			resistanceTitle() {
				if (this.sceneCategory == 4) return '推广异议';
				return this.sceneCategory == 3 ? '问题阻塞' : '当前抗性';
			},
			sceneChips() {
				return this.arrayFrom(this.analysis.triggeredScenes).slice(0, 8).map(item => {
					if (typeof item === 'string') {
						return {
							sceneName: item,
							confidence: 0
						};
					}
					return {
						sceneName: item.sceneName || item.name || item.sceneCode || '细分场景',
						sceneCode: item.sceneCode,
						confidence: this.clampScore(item.confidence)
					};
				});
			},
			scoreItems() {
				const scores = this.analysis.scores || {};
				const metas = this.sceneCategory == 2 ? CUSTOMER_SCORE_META : (this.sceneCategory == 4 ?
					CHANNEL_SCORE_META : (this.sceneCategory == 3 ? MEETING_SCORE_META : LANDLORD_SCORE_META));
				const rows = [];
				metas.forEach(meta => {
					const raw = scores[meta.key] !== undefined ? scores[meta.key] : this.analysis[meta.key];
					if (raw === undefined || raw === null || raw === '') return;
					const value = this.clampScore(raw);
					rows.push({
						key: meta.key,
						label: meta.label,
						percent: value,
						valueText: `${value}`
					});
				});
				Object.keys(scores).forEach(key => {
					if (rows.find(item => item.key === key)) return;
					const value = this.clampScore(scores[key]);
					rows.push({
						key,
						label: this.labelForKey(key),
						percent: value,
						valueText: `${value}`
					});
				});
				return rows.slice(0, 6);
			},
			extractedRows() {
				const info = this.analysis.extractedInfo || {};
				return Object.keys(info).filter(key => info[key] !== undefined && info[key] !== null && String(info[key])
						.trim() !== '')
					.slice(0, 6)
					.map(key => ({
						key,
						label: this.labelForKey(key),
						value: this.valueToText(info[key])
					}));
			},
			judgmentSubText() {
				if (this.sceneCategory == 2) return '客户意向、需求和抗性判断';
				if (this.sceneCategory == 3) return '会议决策、待办和执行风险判断';
				if (this.sceneCategory == 4) return '房源推广、渠道反馈和合作机会判断';
				return '业主意向、信任和委托推进判断';
			},
			recentTranscriptList() {
				return this.transcriptList.filter(item => item.key !== 'partial-current' && item.final !== false).slice(-
					5);
			},
			transcriptPanelList() {
				return this.transcriptList.filter(item => item.key !== 'partial-current' && item.final !== false);
			},
			currentPartialTranscript() {
				return this.transcriptList.find(item => item.key === 'partial-current' || item.final === false) || null;
			},
			footerHintText() {
				if (this.isCompleted) return '报告已生成，可查看完整分析';
				if (this.isFailed) return '分析出现异常，请刷新状态或稍后重试';
				if (this.isFinishing || this.finishing) return '录音已结束，AI正在生成最终报告';
				if (this.isUnclaimedMode) return '补分析完成后自动生成报告';
				return '沟通结束后点击结束场景生成报告';
			},
			canEndScene() {
				return !this.isCompleted && !this.isFailed && !this.isFinishing && !this.isUnclaimedMode;
			},
			canViewReport() {
				return this.isCompleted || this.reportReady;
			},
			canSyncFields() {
				return this.sceneCategory == 1 || this.sceneCategory == 2;
			},
			endButtonText() {
				return this.finishing ? '结束中' : '结束场景';
			}
		},
		onLoad(options = {}) {
			console.log('页面参数', options);
			this.pageClosed = false;
			this.sessionId = decodeURIComponent(options.sessionId || '');
			this.deviceNo = decodeURIComponent(options.deviceNo || '');
			this.deviceType = decodeURIComponent(options.deviceType || '');
			this.deviceName = decodeURIComponent(options.deviceName || '');
			this.glassesPreparedFromRoute = String(options.glassesPrepared || '') === '1';
			this.recordId = decodeURIComponent(options.recordId || '');
			this.sceneCategory = Number(options.sceneCategory || 1);
			this.sceneNames = decodeURIComponent(options.sceneNames || '');
			this.selectedSceneCodes = decodeURIComponent(options.selectedSceneCodes || '');
			this.businessId = decodeURIComponent(options.businessId || '');
			this.businessName = decodeURIComponent(options.businessName || '');
			this.businessType = decodeURIComponent(options.businessType || 'NONE');
			this.businessObjectsText = decodeURIComponent(options.businessObjects || '');
			this.businessObjects = this.parseBusinessObjects(this.businessObjectsText);
			this.startTimer();
			if (this.isAiGlassesMode && !this.sessionId) {
				this.startAiGlassesRuntime();
				return;
			}
			this.loadStatus();
			this.connectWs();
			this.startPolling();
		},
		onShow() {
			if (this.sessionId && !this.pageClosed) {
				this.loadStatus();
			}
		},
		onUnload() {
			this.clearRuntime();
		},
		methods: {
			parseBusinessObjects(text = '') {
				if (!text) return [];
				try {
					const parsed = JSON.parse(text);
					return Array.isArray(parsed) ? parsed : [];
				} catch (error) {
					return [];
				}
			},
			startTimer() {
				this.stopTimer();
				this.timer = setInterval(() => {
					this.updateDuration();
				}, 1000);
				this.updateDuration();
			},
			stopTimer() {
				if (this.timer) {
					clearInterval(this.timer);
					this.timer = null;
				}
			},
			clearRuntime() {
				const shouldStopNativeAudio = this.isAiGlassesMode && (this.teleprompterRunning || this.glassesState
					.audioStarted ||
					this.glassesAudioSequence > 0);
				this.pageClosed = true;
				this.glassesRuntimeStopping = true;
				this.teleprompterRunning = false;
				this.stopTimer();
				this.stopPolling();
				this.unbindRokidEvents();
				if (rokidGlass.clearEventHandlers) rokidGlass.clearEventHandlers();
				this.clearGlassesReconnectTimer();
				if (shouldStopNativeAudio) {
					rokidGlass.stopAudioReady({
						type: 'test',
						iosRecordType: 'test',
						recordType: 'test'
					}).catch(() => {}).finally(() => {
						rokidGlass.closeCustomView().catch(() => {});
						if (rokidGlass.release) rokidGlass.release().catch(() => {});
					});
				}
				this.closeGlassesAudioSocket(true);
				this.closeRealtimeSocket();
			},
			connectWs() {
				if (!this.sessionId || this.finishing || this.isCompleted || this.isFinishing || this.isFailed || this
					.pageClosed) return;
				this.wsState = this.reconnectCount > 0 ? 'reconnecting' : 'connecting';
				this.$u.api.aiSceneCreateWsTicket({
					sessionId: this.sessionId
				}).then(res => {
					const data = (res && res.data) || {};
					if (!data.ticket && !data.path) {
						this.enterRealtimeFallback();
						return;
					}
					const path = data.path ||
						`/system/aiScene/ws?sessionId=${this.sessionId}&ticket=${data.ticket}`;
					const base = this.getApiBase();
					const wsBase = base.replace(/^https/i, 'wss').replace(/^http/i, 'ws');
					this.socketTask = uni.connectSocket({
						url: `${wsBase}${path}`,
						header: this.getSocketHeader(),
						success: () => {}
					});
					this.socketTask.onOpen(() => {
						this.wsConnected = true;
						this.wsState = 'connected';
						this.reconnectCount = 0;
					});
					this.socketTask.onMessage(res => {
						this.handleWsMessage(res.data);
					});
					this.socketTask.onClose(() => {
						this.enterRealtimeFallback();
					});
					this.socketTask.onError(err => {
						console.log(err);
						this.enterRealtimeFallback();
					});
				}).catch(err => {
					console.log(err);
					this.enterRealtimeFallback();
				});
			},
			enterRealtimeFallback() {
				this.wsConnected = false;
				if (this.pageClosed || this.finishing || this.isCompleted || this.isFinishing || this.isFailed) return;
				this.wsState = 'fallback';
				this.startPolling();
				this.scheduleReconnect();
			},
			scheduleReconnect() {
				if (this.reconnectTimer || this.reconnectCount >= 5 || this.pageClosed || this.finishing || this
					.isCompleted || this.isFinishing || this.isFailed) return;
				const delay = Math.min(10000, 1200 * (this.reconnectCount + 1));
				this.reconnectTimer = setTimeout(() => {
					this.reconnectTimer = null;
					this.reconnectCount += 1;
					this.connectWs();
				}, delay);
			},
			closeRealtimeSocket() {
				const task = this.socketTask;
				this.socketTask = null;
				this.wsConnected = false;
				if (!task) return;
				try {
					task.close();
				} catch (e) {}
			},
			startPolling() {
				if (this.pollTimer || !this.sessionId) return;
				this.pollTimer = setInterval(() => {
					this.loadStatus();
				}, 5000);
			},
			stopPolling() {
				if (this.pollTimer) {
					clearInterval(this.pollTimer);
					this.pollTimer = null;
				}
				if (this.reconnectTimer) {
					clearTimeout(this.reconnectTimer);
					this.reconnectTimer = null;
				}
			},
			getApiBase() {
				return (uni.getStorageSync('baseUrl') || 'https://www.tcwang.cc:7000/').replace(/\/$/, '');
			},
			getSocketHeader() {
				const userInfo = uni.getStorageSync('userInfo') || {};
				const token = userInfo.token || uni.getStorageSync('token') || '';
				return {
					Source: '3',
					from: 'agent',
					token,
					'Access-Token': token
				};
			},
			handleWsMessage(raw) {
				let payload = raw;
				try {
					payload = typeof raw === 'string' ? JSON.parse(raw) : raw;
				} catch (e) {
					return;
				}
				const event = payload.event;
				const data = payload.data || {};
				if (event === 'connected') return;
				if (event === 'transcript.partial' || event === 'transcript.final') {
					this.mergeTranscript(data, event === 'transcript.partial');
					return;
				}
				if (event === 'analysis.delta' || event === 'analysis.final') {
					this.applyAnalysis(data.analysis || data.report || data);
					if (event === 'analysis.final') {
						this.status = STATUS_COMPLETED;
						this.reportReady = true;
						this.finishing = false;
						this.endedAtMs = this.endedAtMs || this.currentServerMs();
						this.updateDuration();
						this.stopPolling();
					}
					return;
				}
				if (event === 'session.status') {
					this.applyRuntimeStatus(data);
					if (data.currentAnalysisJson) {
						this.applyAnalysis(data.currentAnalysisJson);
					}
				}
			},
			getRokidAudioCodecType() {
				return 1;
			},
			async startAiGlassesRuntime() {
				uni.showLoading({
					title: '启动眼镜录音'
				});
				try {
					this.latestSnapshotError = '';
					this.glassesAudioSequence = 0;
					this.glassesAudioBytes = 0;
					this.glassesAudioSendQueue = [];
					this.glassesPcmPendingBytes = [];
					this.glassesPcmPendingSize = 0;
					this.glassesAudioUploadKey = '';
					this.glassesAudioChunkIndex = 0;
					this.glassesLastSocketSentAt = 0;
					this.glassesSocketSending = false;
					this.glassesSocketSentChunks = 0;
					this.glassesSocketSentBytes = 0;
					this.glassesSocketSendFailed = 0;
					this.glassesDroppedChunks = 0;
					this.glassesSkippedSilentChunks = 0;
					this.glassesAudioCodecType = null;
					this.glassesSilentChunks = 0;
					this.glassesVoiceRun = 0;
					this.glassesVoiceSilenceRun = 0;
					this.glassesVoiceActive = false;
					this.glassesVoiceCandidateQueue = [];
					this.lastGlassesAudioLevel = {};
					this.lastGlassesAudioGain = 1;
					const forceJsUpload = String(uni.getStorageSync('rokidForceJsUpload') || '').toLowerCase() ===
						'true';
					this.glassesNativeUpload = !forceJsUpload;
					this.glassesRuntimeStopping = false;
					this.localAiGlassesStartFailed = false;
					this.glassesStartupPhase = 'preparing';
					if (rokidGlass.clearEventHandlers) rokidGlass.clearEventHandlers();
					this.unbindRokidEvent = null;
					this.bindRokidEvents();
					await this.prepareRokidGlasses();
					this.glassesStartupPhase = 'creatingSession';
					const startRes = await this.createAiGlassesSession();
					console.log('startRes----------', startRes);
					const data = (startRes && startRes.data) || {};
					this.sessionId = data.aiSceneSessionId || data.sessionId || '';
					this.recordId = data.recordId || data.deviceAudioRecordId || data.audioRecordId || data.id || this
						.recordId || this.sessionId;
					if (!this.sessionId) throw new Error('AI会话创建失败');
					this.startedAtMs = this.startedAtMs || this.currentServerMs();
					this.connectWs();
					this.startPolling();
					this.loadStatus();
					this.glassesStartupPhase = 'connectingSocket';
					this.prepareGlassesWsAudioUpload();
					if (this.glassesNativeUpload) {
						this.glassesSocketUrl = this.getGlassesSocketUrl();
						this.glassesSocketState = 'connecting';
						this.glassesSocketOpened = false;
					} else {
						await this.ensureGlassesAudioSocket();
					}
					this.glassesStartupPhase = 'startingAudio';
					await this.ensureRokidCustomViewBeforeAudio();
					this.teleprompterRunning = true;
					this.glassesAudioStartConfirmed = false;
					this.glassesAudioError = '';
					const beforeAudioSequence = this.glassesAudioSequence;
					const beforeSentChunks = this.glassesSocketSentChunks;
					const codecType = this.getRokidAudioCodecType();
					const interruptAiWakeStorage = uni.getStorageSync('rokidInterruptAiWake');
					const interruptAiWake = interruptAiWakeStorage === true || String(interruptAiWakeStorage || '')
						.toLowerCase() === 'true';
					this.glassesAudioCodecType = codecType;
					console.log('Rokid scene startAudioRecord before', {
						beforeAudioSequence,
						beforeSentChunks,
						socketOpened: this.glassesSocketOpened,
						socketState: this.glassesSocketState,
						codecType,
						interruptAiWake,
						deviceName: this.deviceName || this.deviceNo
					});
					const res = await rokidGlass.startAudioReady({
						type: 'teleprompter',
						iosRecordType: 'test',
						recordType: 'test',
						codecType,
						codec: 'pcm',
						audioCodec: 'pcm',
						mode: 'antClose',
						audioMode: 'antClose',
						interruptAiWake,
						streamOnly: true,
						nativeUpload: this.glassesNativeUpload,
						wsUrl: this.glassesSocketUrl,
						headers: this.getSocketHeader(),
						sessionData: this.buildGlassesSessionData('audio.chunk'),
						startPayload: this.buildGlassesSessionPayload('session.start'),
						stopPayload: this.buildGlassesSessionPayload('session.stop'),
						chunkBytes: this.glassesNativeUpload ? this.getGlassesNativeUploadChunkBytes() : this
							.getGlassesPcmChunkBytes(),
						maxQueueChunks: this.glassesNativeUpload ? 240 : 80,
						debugAudio: false
					});
					console.log('Rokid scene startAudioRecord after', res);
					this.mergeGlassesState(res);
					this.glassesAudioPath = res && res.pcmPath ? res.pcmPath : '';
					await this.waitForGlassesAudioStarted(beforeAudioSequence, beforeSentChunks);
					this.updateGlassesTeleprompterFromScripts(true).catch(() => {});
					this.glassesStartupPhase = 'running';
					uni.showToast({
						title: '眼镜录音已开始',
						icon: 'none'
					});
				} catch (error) {
					this.teleprompterRunning = false;
					this.glassesAudioStartConfirmed = false;
					this.glassesStartupPhase = 'failed';
					this.glassesState = Object.assign({}, this.glassesState, {
						audioStarted: false
					});
					const message = this.formatErrorMessage(error, '眼镜录音启动失败');
					this.latestSnapshotError = message;
					await this.handleAiGlassesStartupFailure(message);
					uni.showToast({
						title: this.latestSnapshotError,
						icon: 'none'
					});
				} finally {
					uni.hideLoading();
				}
			},
			bindRokidEvents() {
				if (this.unbindRokidEvent) return;
				try {
					this.unbindRokidEvent = rokidGlass.onEvent((event) => {
						if (!event) return;
						if (event.event === 'audioChunk' && (this.finishing || this.glassesRuntimeStopping || !this
								.teleprompterRunning)) return;
						this.logRokidEvent(event);
						if (typeof event === 'object') this.mergeGlassesState(event);
						if (event.event === 'customViewOpened') {
							this.glassesCustomViewOpenedAtMs = Date.now();
						}
						if (event.event === 'nativeUploadStarted') {
							this.glassesSocketState = 'connected';
							this.glassesSocketOpened = true;
						}
						if (event.event === 'nativeUploadStats' || event.event === 'nativeUploadStopped' || event.event ===
							'nativeUploadError') {
							this.glassesSocketSentChunks = Number(event.nativeUploadSentChunks || this.glassesSocketSentChunks ||
								0);
							this.glassesSocketSentBytes = Number(event.nativeUploadSentBytes || this.glassesSocketSentBytes || 0);
							this.glassesDroppedChunks = Number(event.nativeUploadDroppedBytes || this.glassesDroppedChunks || 0);
							if (event.event === 'nativeUploadStopped') {
								this.glassesSocketOpened = false;
								this.glassesSocketState = 'closed';
							}
							if (event.event === 'nativeUploadError') {
								this.glassesSocketOpened = false;
								this.glassesSocketState = 'error';
								this.glassesSocketSendFailed += 1;
								this.glassesAudioError = event.nativeUploadError || event.message || '原生音频上传异常';
							}
						}
						if (event.event === 'nativeUploadMessage' && event.message) {
							this.handleTeleprompterMessage(event.message).catch(() => {});
						}
						if (event.event === 'audioChunk') this.glassesAudioStartConfirmed = true;
						const eventName = String(event.event || event.type || event.name || '');
						if (event.success === false && /audio|record/i.test(eventName) && (event.message || event
								.errorMsg)) {
							this.glassesAudioError = event.message || event.errorMsg;
						}
						if (event.event === 'audioError') {
							this.glassesAudioError = event.message || `audioError ${event.errorCode || ''}`;
						}
						if (event.event === 'audioChunk' && !this.glassesNativeUpload) this.sendGlassesAudioChunk(event);
						this.updateRokidEventDebug(event);
					});
				} catch (error) {
					this.latestSnapshotError = this.formatErrorMessage(error, 'AI眼镜插件不可用');
				}
			},
			logRokidEvent(event = {}) {
				try {
					const eventName = String(event.event || event.type || event.name || 'unknown');
					if (eventName !== 'audioChunk') {
						const signature = `${eventName}:${event.success}:${event.message || event.errorMsg || ''}`;
						const now = Date.now();
						if (this._lastRokidLogSignature === signature && now - Number(this._lastRokidLogAt || 0) < 5000) {
							return;
						}
						this._lastRokidLogSignature = signature;
						this._lastRokidLogAt = now;
					}
					const payload = Object.assign({}, event);
					if (payload.base64) payload.base64 = `[base64:${String(payload.base64).length}]`;
					if (payload.payloadBase64) payload.payloadBase64 = `[base64:${String(payload.payloadBase64).length}]`;
					if (payload.chunkBase64) payload.chunkBase64 = `[base64:${String(payload.chunkBase64).length}]`;
					if (payload.arrayBuffer) payload.arrayBuffer = `[arrayBuffer:${payload.arrayBuffer.byteLength || 0}]`;
					if (payload.data && typeof payload.data === 'string' && payload.data.length > 120) {
						payload.data = `[string:${payload.data.length}]`;
					}
					console.log('RokidGlass native event', payload);
				} catch (error) {}
			},
			updateRokidEventDebug(event = {}) {
				const name = String(event.event || event.type || event.name || 'unknown');
				this.lastRokidEventName = name;
				const parts = [];
				if (event.bridgeVersion) parts.push(event.bridgeVersion);
				if (event.recordType) parts.push(`type=${event.recordType}`);
				if (event.audioCodecType !== undefined) parts.push(`codecType=${event.audioCodecType}`);
				else if (event.codecType !== undefined) parts.push(`codecType=${event.codecType}`);
				else if (this.glassesAudioCodecType !== null && this.glassesAudioCodecType !== undefined) parts.push(
					`codecType=${this.glassesAudioCodecType}`);
				if (event.audioChunkCount !== undefined) parts.push(`chunk=${event.audioChunkCount}`);
				if (event.nativeUploadState) parts.push(`upload=${event.nativeUploadState}`);
				if (event.nativeUploadSentChunks !== undefined) parts.push(`sent=${event.nativeUploadSentChunks}`);
				if (event.nativeUploadDroppedBytes) parts.push(`dropBytes=${event.nativeUploadDroppedBytes}`);
				if (event.bytes !== undefined) parts.push(`${event.bytes}B`);
				if (event.maxAbs !== undefined) parts.push(`max=${event.maxAbs}`);
				if (event.avgAbs !== undefined) parts.push(`avg=${Number(event.avgAbs || 0).toFixed(1)}`);
				if (event.silentLike !== undefined) parts.push(`silent=${event.silentLike}`);
				if (event.bleConnected !== undefined) parts.push(`ble=${event.bleConnected}`);
				if (event.connectedDeviceName) parts.push(event.connectedDeviceName);
				if (name === 'audioChunk') {
					parts.push(`native=${this.glassesAudioSequence}`);
					parts.push(`sent=${this.glassesSocketSentChunks}`);
					parts.push(`pending=${this.glassesPcmPendingSize || 0}B`);
					if (this.glassesSocketSendFailed) parts.push(`fail=${this.glassesSocketSendFailed}`);
					if (this.glassesDroppedChunks) parts.push(`drop=${this.glassesDroppedChunks}`);
					if (this.glassesSkippedSilentChunks) parts.push(`skip=${this.glassesSkippedSilentChunks}`);
				}
				this.lastRokidEventDetail = parts.join('，');
			},
			unbindRokidEvents() {
				if (!this.unbindRokidEvent) return;
				try {
					this.unbindRokidEvent();
				} catch (error) {}
				this.unbindRokidEvent = null;
			},
			async prepareRokidGlasses() {
				const iosRuntime = this.isIOSRuntime();
				console.log('Rokid prepareCapture path', {
					iosRuntime,
					preparedFromRoute: this.glassesPreparedFromRoute,
					cachedPrepared: this.canUseCachedPreparedGlasses(),
					forcePrepare: false
				});
				if (iosRuntime && this.glassesPreparedFromRoute) {
					this.markRokidGlassesPrepared();
					return;
				}
				if (iosRuntime && this.canUseCachedPreparedGlasses()) {
					await this.warmupCachedRokidGlasses();
					this.markRokidGlassesPrepared();
					return;
				}
				const prepareStartedAt = Date.now();
				try {
					const res = await rokidGlass.prepareCapture({
						forceOpenCustomView: !iosRuntime,
						title: 'AI提词器',
						text: '录音中，等待AI分析...',
						forceOpenDelay: 900,
						openDelay: 900
					});
					this.mergeGlassesState(res);
					try {
						const info = await rokidGlass.requestGlassDeviceInfo({
							timeout: 3000
						});
						this.mergeGlassesState(info);
					} catch (error) {}
				} catch (error) {
					if (iosRuntime && this.isCustomViewOpenFailure(error)) {
						const openedByEvent = await this.waitForRokidCustomViewOpened(prepareStartedAt, 1600);
						if (openedByEvent) {
							this.mergeGlassesState({
								ready: true,
								sceneReady: true,
								sessionType: 'customView'
							});
							return;
						}
					}
					if (iosRuntime && this.isSoftGlassesPrepareError(error)) {
						this.markRokidGlassesPrepared();
						return;
					}
					throw error;
				}
			},
			isIOSRuntime() {
				try {
					const info = uni.getSystemInfoSync ? uni.getSystemInfoSync() : {};
					return String(info.platform || info.osName || '').toLowerCase() === 'ios';
				} catch (error) {
					return false;
				}
			},
			isCustomViewOpenFailure(error) {
				const message = this.formatErrorMessage(error, '');
				return /openCustomView|customViewOpen|custom view|提词场景|场景打开/i.test(String(message || ''));
			},
			waitForRokidCustomViewOpened(since = 0, timeout = 1500) {
				if (this.glassesCustomViewOpenedAtMs && this.glassesCustomViewOpenedAtMs >= since) {
					return Promise.resolve(true);
				}
				return new Promise(resolve => {
					const startedAt = Date.now();
					const timer = setInterval(() => {
						if (this.glassesCustomViewOpenedAtMs && this.glassesCustomViewOpenedAtMs >=
							since) {
							clearInterval(timer);
							resolve(true);
							return;
						}
						if (Date.now() - startedAt >= timeout) {
							clearInterval(timer);
							resolve(false);
						}
					}, 100);
				});
			},
			async warmupCachedRokidGlasses() {
				if (!this.isIOSRuntime()) return;
				const runWithTimeout = (task, timeout = 2500) => new Promise(resolve => {
					let done = false;
					const finish = () => {
						if (done) return;
						done = true;
						resolve();
					};
					setTimeout(finish, timeout);
					Promise.resolve(task()).then(finish).catch(finish);
				});
				const openWithTimeout = (task, timeout = 7000) => new Promise((resolve, reject) => {
					let done = false;
					const finish = (handler, value) => {
						if (done) return;
						done = true;
						handler(value);
					};
					setTimeout(() => finish(reject, new Error('眼镜提词场景打开超时')), timeout);
					Promise.resolve(task()).then(value => finish(resolve, value)).catch(error => finish(reject,
						error));
				});
				await runWithTimeout(() => rokidGlass.initSDK({
					sessionType: 'customView'
				}), 2500);
				await runWithTimeout(() => rokidGlass.connectCustomView({
					sessionType: 'customView'
				}), 2500);
				let lastError = null;
				for (let attempt = 0; attempt < 2; attempt += 1) {
					if (attempt > 0) {
						try {
							await rokidGlass.closeCustomView();
						} catch (error) {}
						await new Promise(resolve => setTimeout(resolve, 800));
					}
					let openStartedAt = 0;
					try {
						openStartedAt = Date.now();
						const res = await openWithTimeout(() => rokidGlass.openCustomView({
							title: 'AI提词器',
							text: 'AI场景准备中，等待录音...',
							nativeTimeout: 6000
						}), 7000);
						this.mergeGlassesState(res);
						lastError = null;
						break;
					} catch (error) {
						if (this.isCustomViewOpenFailure(error)) {
							const openedByEvent = await this.waitForRokidCustomViewOpened(openStartedAt, 1600);
							if (openedByEvent) {
								this.mergeGlassesState({
									ready: true,
									sceneReady: true,
									sessionType: 'customView'
								});
								lastError = null;
								break;
							}
						}
						lastError = error;
						console.log('Rokid cached customView open failed', this.formatErrorMessage(error, ''));
					}
				}
				if (lastError) throw lastError;
				await new Promise(resolve => setTimeout(resolve, 1200));
			},
			async ensureRokidCustomViewBeforeAudio() {
				const openStartedAt = Date.now();
				const iosRuntime = this.isIOSRuntime();
				try {
					const openRes = await rokidGlass.ensureCustomViewOpened({
						title: 'AI提词器',
						text: 'AI场景已连接，正在启动录音...',
						openRetries: iosRuntime ? 4 : 3,
						openDelay: iosRuntime ? 1800 : 700,
						openWaitMs: iosRuntime ? 8000 : 10000,
						nativeTimeout: iosRuntime ? 12000 : 13000
					});
					this.mergeGlassesState(openRes);
				} catch (error) {
					console.log('Rokid scene ensureCustomViewOpened before audio failed', this.formatErrorMessage(error,
						''));
					throw error;
				}
				const openedByEvent = await this.waitForRokidCustomViewOpened(openStartedAt, iosRuntime ? 1800 :
					3000);
				if (!openedByEvent) {
					try {
						const latest = await rokidGlass.queryRuntimeDiagnostics({
							nativeTimeout: 3000
						});
						this.mergeGlassesState(latest);
						if (!latest || (!latest.sceneReady && !latest.sdkCustomViewOpen)) {
							throw new Error('眼镜提词场景未确认打开');
						}
					} catch (error) {
						throw error;
					}
				}
				try {
					await rokidGlass.updateCustomView({
						text: 'AI场景已连接，正在启动录音...',
						nativeTimeout: 3000
					});
				} catch (error) {
					console.log('Rokid scene updateCustomView before audio ignored', this.formatErrorMessage(error,
						''));
				}
				await new Promise(resolve => setTimeout(resolve, this.isIOSRuntime() ? 500 : 1000));
			},
			canUseCachedPreparedGlasses() {
				const cachedName = String(uni.getStorageSync('boundGlassesDeviceName') || '').trim();
				const cachedChannel = String(uni.getStorageSync('glassesChannelId') || '').trim();
				const routeName = String(this.deviceName || this.deviceNo || '').trim();
				const matched = cachedName && (!routeName || cachedName.toLowerCase() === routeName.toLowerCase());
				return !!(this.glassesPreparedFromRoute || matched || cachedChannel);
			},
			markRokidGlassesPrepared() {
				const deviceName = String(uni.getStorageSync('boundGlassesDeviceName') || this.deviceName || this
						.deviceNo || '')
					.trim();
				this.mergeGlassesState({
					ready: true,
					sceneReady: true,
					hasToken: true,
					glassId: deviceName,
					deviceName,
					glassIdSource: 'cachedPrepared',
					glassIdStable: false
				});
				if (deviceName) {
					uni.setStorageSync('boundGlassesDeviceName', deviceName);
					uni.setStorageSync('glassesChannelId', deviceName);
				}
			},
			isSoftGlassesPrepareError(error) {
				const message = this.formatErrorMessage(error, '');
				if (this.isIOSRuntime()) return false;
				if (/openCustomView|customViewOpen|custom view|提词场景|场景打开/i.test(String(message || ''))) return false;
				return /眼镜准备超时|prepare.*timeout|openCustomView.*timeout|initializeClient.*timeout/i.test(String(
					message || ''));
			},
			async handleAiGlassesStartupFailure(message = '') {
				this.localAiGlassesStartFailed = true;
				this.glassesRuntimeStopping = true;
				this.teleprompterRunning = false;
				this.glassesAudioStartConfirmed = false;
				this.status = STATUS_FAILED;
				this.finishing = false;
				this.stopPolling();
				this.closeRealtimeSocket();
				this.closeGlassesAudioSocket(true);
				this.glassesState = Object.assign({}, this.glassesState, {
					audioStarted: false
				});
				try {
					await rokidGlass.stopAudioReady({
						type: 'test',
						iosRecordType: 'test',
						recordType: 'test'
					});
				} catch (error) {}
				this.unbindRokidEvents();
				if (rokidGlass.clearEventHandlers) rokidGlass.clearEventHandlers();
				try {
					await rokidGlass.closeCustomView();
				} catch (error) {}
				if (rokidGlass.release) {
					try {
						await rokidGlass.release();
					} catch (error) {}
				}
				if (this.sessionId) {
					try {
						await this.$u.api.aiSceneFinishSession({
							sessionId: this.sessionId,
							deviceNo: this.deviceNo,
							deviceType: 'AI_GLASSES',
							failReason: message
						});
					} catch (error) {}
				}
			},
			waitForGlassesAudioStarted(startSequence = 0, startSentChunks = 0, timeout = 15000) {
				return new Promise((resolve, reject) => {
					const startedAt = Date.now();
					const timer = setInterval(() => {
						if (this.glassesAudioError) {
							clearInterval(timer);
							rokidGlass.stopAudioReady({
								type: 'test',
								iosRecordType: 'test',
								recordType: 'test'
							}).catch(() => {});
							reject(new Error(`眼镜音频启动失败：${this.glassesAudioError}`));
							return;
						}
						const gotNativeChunk = this.glassesAudioSequence > startSequence;
						const gotNativeUpload = this.glassesNativeUpload && this.glassesSocketSentChunks > startSentChunks;
						if (gotNativeChunk || gotNativeUpload) {
							clearInterval(timer);
							this.glassesAudioStartConfirmed = true;
							resolve(true);
							return;
						}
						if (Date.now() - startedAt >= timeout) {
							clearInterval(timer);
							rokidGlass.stopAudioReady({
								type: 'test',
								iosRecordType: 'test',
								recordType: 'test'
							}).catch(() => {});
							reject(new Error('眼镜未返回音频流，请确认 Rokid AI App 已连接眼镜且 CustomView 场景已打开'));
						}
					}, 150);
				});
			},
			createAiGlassesSession() {
				return this.$u.api.dudutalkAudioStart({
					deviceNo: this.deviceNo,
					type: 2,
					deviceType: 'AI_GLASSES',
					deviceSource: 'AI_GLASSES',
					recordSource: 'AI_GLASSES',
					skipDeviceAudioStart: true,
					deviceName: this.deviceName || this.deviceNo,
					channelId: this.getGlassesChannelId(),
					clientId: this.getTeleprompterClientId(),
					aiSceneCategory: this.sceneCategory,
					selectedSceneCodes: this.selectedSceneCodes,
					aiSceneBusinessType: this.businessType,
					aiSceneBusinessId: this.businessId || null,
					aiSceneBusinessName: this.businessName,
					aiSceneBusinessDisplayName: this.businessName,
					aiSceneBusinessSubtitle: '',
					aiSceneBusinessObjects: this.businessObjectsText
				});
			},
			mergeGlassesState(next = {}) {
				if (!next || typeof next !== 'object') return;
				const info = next.glassDeviceInfo || {};
				const normalized = Object.assign({}, next);
				if (normalized.audioCodecType !== undefined) this.glassesAudioCodecType = normalized.audioCodecType;
				else if (normalized.codecType !== undefined) this.glassesAudioCodecType = normalized.codecType;
				if (!normalized.deviceName && info.deviceName) normalized.deviceName = info.deviceName;
				if (!normalized.glassId && info.glassId) normalized.glassId = info.glassId;
				if (!normalized.deviceId && info.deviceId) normalized.deviceId = info.deviceId;
				if (!normalized.sn && info.sn) normalized.sn = info.sn;
				this.glassesState = Object.assign({}, this.glassesState, normalized);
			},
			getGlassesIdentifier() {
				const source = this.glassesState || {};
				const glassId = String(source.glassId || '').trim();
				const deviceId = String(source.deviceId || '').trim();
				const sn = String(source.sn || '').trim();
				const deviceName = String(source.deviceName || '').trim();
				const glassIdSource = String(source.glassIdSource || '').trim();
				if (glassIdSource === 'bluetoothName') return sn || deviceName || glassId || deviceId || this.deviceNo;
				return glassId || deviceId || sn || deviceName || this.deviceNo;
			},
			getGlassesIdentifierSource() {
				const source = this.glassesState || {};
				const glassIdSource = String(source.glassIdSource || '').trim();
				if (source.glassId && glassIdSource !== 'bluetoothName') return glassIdSource || 'glassId';
				if (source.deviceId && glassIdSource !== 'bluetoothName') return 'deviceId';
				if (source.sn) return 'sn';
				if (source.deviceName) return 'deviceName';
				return 'boundDevice';
			},
			getGlassesChannelId() {
				const identifier = String(this.getGlassesIdentifier() || '').trim();
				const clientId = String(this.getTeleprompterClientId() || '').trim();
				if (identifier && identifier !== clientId) return identifier;
				return this.getOrCreateGlassesLocalChannelId();
			},
			getTeleprompterClientId() {
				const userInfo = uni.getStorageSync('userInfo') || {};
				const employeeId = (this.$store && this.$store.getters && this.$store.getters.employeeId) || uni
					.getStorageSync('employeeId') || userInfo.employeeId || userInfo.id || userInfo.userId || '';
				const raw = String(this.recordId || this.sessionId || employeeId || 'agent-app').trim();
				return raw.indexOf('agent-app-') === 0 ? raw : `agent-app-${raw}`;
			},
			getOrCreateGlassesLocalChannelId() {
				const clientId = this.getTeleprompterClientId() || 'agent-app';
				let channelId = String(uni.getStorageSync('glassesLocalChannelId') || '').trim();
				if (!channelId || channelId === clientId) {
					channelId = `glasses-room-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
					uni.setStorageSync('glassesLocalChannelId', channelId);
				}
				return channelId;
			},
			getGlassesSocketUrl() {
				const baseUrl = this.getApiBase();
				const path = String(uni.getStorageSync('glassesWsPath') || '/system/commonWebSocket/glasses/ws').trim();
				const fullPath = path.startsWith('/') ? path : `/${path}`;
				const channelId = encodeURIComponent(this.getGlassesChannelId());
				const clientId = encodeURIComponent(this.getTeleprompterClientId());
				return baseUrl.replace(/^https/i, 'wss').replace(/^http/i, 'ws') +
					`${fullPath}?channelId=${channelId}&clientId=${clientId}`;
			},
			async ensureGlassesAudioSocket(retries = 2) {
				let lastError = null;
				for (let attempt = 0; attempt <= retries; attempt += 1) {
					try {
						await this.connectGlassesAudioSocket();
						return true;
					} catch (error) {
						lastError = error;
						this.latestSnapshotError = this.formatErrorMessage(error, '眼镜音频 WebSocket 连接失败');
						if (attempt < retries) {
							await new Promise(resolve => setTimeout(resolve, 800 + attempt * 700));
						}
					}
				}
				throw lastError || new Error('眼镜音频 WebSocket 连接失败');
			},
			connectGlassesAudioSocket() {
				return new Promise((resolve, reject) => {
					this.clearGlassesReconnectTimer();
					this.closeGlassesAudioSocket(true, false);
					let settled = false;
					let opened = false;
					this.manualClosingGlassesSocket = false;
					this.glassesSocketState = 'connecting';
					this.glassesSocketUrl = this.getGlassesSocketUrl();
					const timer = setTimeout(() => {
						if (settled) return;
						settled = true;
						this.glassesSocketState = 'timeout';
						reject(new Error('眼镜音频 WebSocket 连接超时'));
					}, 10000);
					console.log('this.glassesSocketUrl------------', this.glassesSocketUrl);
					const socketTask = uni.connectSocket({
						url: this.glassesSocketUrl,
						header: this.getSocketHeader(),
						fail: () => {
							if (!settled) {
								settled = true;
								clearTimeout(timer);
								this.glassesSocketState = 'failed';
								reject(new Error('眼镜音频 WebSocket 创建失败'));
							}
						}
					});
					this.glassesSocketTask = socketTask;
					socketTask.onOpen(() => {
						console.log('open---------------');
						if (this.glassesSocketTask !== socketTask) return;
						opened = true;
						this.glassesSocketOpened = true;
						this.glassesSocketState = 'connected';
						this.glassesReconnectCount = 0;
						this.latestSnapshotError = '';
						this.sendGlassesSocketJson(this.buildGlassesSessionPayload('session.start'));
						if (!settled) {
							settled = true;
							clearTimeout(timer);
							resolve();
						}
					});
					socketTask.onMessage((res) => {
						console.log('onMessage---------------', res);
						if (this.glassesSocketTask !== socketTask) return;
						this.handleTeleprompterMessage(res.data).catch(() => {});
					});
					socketTask.onError(() => {
						console.log('onError---------------');
						if (this.glassesSocketTask !== socketTask) return;
						this.glassesSocketOpened = false;
						this.glassesSocketState = 'error';
						if (!settled) {
							settled = true;
							clearTimeout(timer);
							reject(new Error('眼镜音频 WebSocket 连接失败'));
							return;
						}
						if (opened) this.scheduleGlassesAudioReconnect();
					});
					socketTask.onClose(() => {
						if (this.glassesSocketTask !== socketTask) return;
						this.glassesSocketOpened = false;
						this.glassesSocketState = 'closed';
						if (this.manualClosingGlassesSocket) {
							this.manualClosingGlassesSocket = false;
							return;
						}
						if (opened) this.scheduleGlassesAudioReconnect();
					});
				});
			},
			clearGlassesReconnectTimer() {
				if (!this.glassesReconnectTimer) return;
				clearTimeout(this.glassesReconnectTimer);
				this.glassesReconnectTimer = null;
			},
			scheduleGlassesAudioReconnect() {
				if (this.pageClosed || this.finishing || !this.isAiGlassesMode || !this.isRunning || !this
					.teleprompterRunning || this.glassesReconnectTimer || this.glassesReconnectCount >= 5) return;
				this.glassesSocketState = 'reconnecting';
				const delay = Math.min(10000, 1000 + this.glassesReconnectCount * 1200);
				this.glassesReconnectTimer = setTimeout(() => {
					this.glassesReconnectTimer = null;
					this.glassesReconnectCount += 1;
					this.connectGlassesAudioSocket().catch(error => {
						this.latestSnapshotError = this.formatErrorMessage(error, '眼镜音频 WebSocket 重连失败');
						this.scheduleGlassesAudioReconnect();
					});
				}, delay);
			},
			closeGlassesAudioSocket(manual = true, clearAudio = manual) {
				const task = this.glassesSocketTask;
				this.glassesSocketTask = null;
				this.glassesSocketOpened = false;
				this.glassesSocketSending = false;
				if (clearAudio) {
					this.glassesAudioSendQueue = [];
					this.glassesPcmPendingBytes = [];
					this.glassesPcmPendingSize = 0;
				}
				if (manual) {
					this.manualClosingGlassesSocket = true;
					this.clearGlassesReconnectTimer();
					this.glassesSocketState = 'closed';
				}
				if (!task) return;
				try {
					task.close();
				} catch (error) {}
			},
			buildGlassesSessionData(event) {
				return {
					type: event,
					sessionId: this.sessionId,
					recordId: this.recordId || '',
					businessType: 'glasses',
					transport: 'json',
					audioTransport: 'websocket-json-base64',
					timestamp: Date.now(),
					...this.glassesDevicePayload(),
					deviceNo: this.deviceNo,
					deviceType: 'AI_GLASSES',
					channelId: this.getGlassesChannelId(),
					channelIdSource: 'glassIdentifier',
					clientId: this.getTeleprompterClientId(),
					aiSceneCategory: this.sceneCategory,
					sceneNames: this.sceneNames,
					selectedSceneCodes: this.selectedSceneCodes,
					aiSceneBusinessType: this.businessType,
					aiSceneBusinessId: this.businessId || null,
					aiSceneBusinessName: this.businessName,
					aiSceneBusinessObjects: this.businessObjectsText,
					uploadKey: this.getGlassesAudioUploadKey(),
					fileName: `${this.getGlassesAudioUploadKey()}.pcm`,
					audioFormat: 'pcm',
					deviceAudioRecordId: this.getDeviceAudioRecordId(),
					codec: 'pcm',
					format: 'pcm_s16le',
					mimeType: 'audio/pcm',
					sampleRate: 16000,
					channels: 1,
					bitsPerSample: 16,
					endian: 'little',
					chunkBytes: this.getGlassesPcmChunkBytes(),
					chunkDurationMs: Math.round(this.getGlassesPcmChunkBytes() / 2 / 16000 * 1000)
				};
			},
			buildGlassesSessionPayload(event) {
				return {
					event,
					data: this.buildGlassesSessionData(event)
				};
			},
			glassesDevicePayload() {
				const deviceId = this.getGlassesIdentifier();
				return {
					glassId: deviceId,
					glassIdSource: this.getGlassesIdentifierSource(),
					glassIdStable: this.getGlassesIdentifierSource() === 'sn' || Boolean(this.glassesState.glassIdStable),
					deviceId,
					sn: this.glassesState.sn || '',
					deviceName: this.glassesState.deviceName || this.deviceName || deviceId,
					bluetoothName: this.glassesState.deviceName || this.deviceName || deviceId
				};
			},
			sendGlassesAudioChunk(event) {
				const rawBuffer = event && (event.arrayBuffer || event.buffer || event.payload) ? (event.arrayBuffer ||
					event.buffer || event.payload) : null;
				const payloadBase64 = event && (event.base64 || event.payloadBase64 || event.chunkBase64 ||
					(typeof event.data === 'string' ? event.data : '')) || '';
				if (!event || (!payloadBase64 && !rawBuffer)) return;
				let audioBuffer = null;
				try {
					audioBuffer = rawBuffer ? this.normalizeArrayBuffer(rawBuffer) : this.base64ToArrayBuffer(
						payloadBase64);
				} catch (error) {
					this.glassesDroppedChunks += 1;
					return;
				}
				if (!audioBuffer || !audioBuffer.byteLength) return;
				this.glassesAudioSequence += 1;
				this.glassesAudioBytes += audioBuffer.byteLength;
				const level = this.getPcmLevelStats(audioBuffer);
				this.updateGlassesAudioLevel(level);
				this.logGlassesAudioLevel('native', this.glassesAudioSequence, level, event);
				if (Number(this.glassesAudioCodecType) !== 1) {
					this.glassesDroppedChunks += 1;
					if (this.glassesAudioSequence <= 3 || this.glassesAudioSequence % 20 === 0) {
						console.log('glasses audio chunk kept local only because codec is not pcm', {
							sequence: this.glassesAudioSequence,
							codecType: this.glassesAudioCodecType,
							bytes: audioBuffer.byteLength,
							firstBytesHex: level.firstBytesHex
						});
					}
					return;
				}
				if (this.shouldDropGlassesNearZeroAudio(level)) {
					this.glassesSkippedSilentChunks += 1;
					if (this.glassesSkippedSilentChunks <= 3 || this.glassesSkippedSilentChunks % 20 === 0) {
						console.log('glasses pcm chunk skipped near-zero', {
							skipped: this.glassesSkippedSilentChunks,
							sequence: this.glassesAudioSequence,
							bytes: level.bytes,
							maxAbs: level.maxAbs,
							avgAbs: level.avgAbs,
							nonZeroSamples: level.nonZeroSamples,
							firstBytesHex: level.firstBytesHex
						});
					}
					return;
				}
				if (this.finishing || !this.isRunning || !this.teleprompterRunning || !this.glassesSocketOpened) {
					this.glassesDroppedChunks += 1;
					if (this.teleprompterRunning && !this.glassesSocketOpened) this.scheduleGlassesAudioReconnect();
					return;
				}
				this.glassesVoiceActive = true;
				this.glassesVoiceRun += 1;
				this.glassesVoiceSilenceRun = level.silentLike ? this.glassesVoiceSilenceRun + 1 : 0;
				const uploadBuffer = this.applyGlassesAudioGain(audioBuffer, level);
				this.appendGlassesPcmBytes(uploadBuffer);
			},
			getGlassesVoiceGate() {
				const minMaxAbs = Number(uni.getStorageSync('glassesAudioMinMaxAbs') || 80);
				const minAvgAbs = Number(uni.getStorageSync('glassesAudioMinAvgAbs') || 8);
				const targetMaxAbs = Number(uni.getStorageSync('glassesAudioTargetMaxAbs') || 8000);
				const maxGain = Number(uni.getStorageSync('glassesAudioMaxGain') || 8);
				return {
					minMaxAbs: Number.isFinite(minMaxAbs) && minMaxAbs > 0 ? minMaxAbs : 80,
					minAvgAbs: Number.isFinite(minAvgAbs) && minAvgAbs > 0 ? minAvgAbs : 8,
					targetMaxAbs: Number.isFinite(targetMaxAbs) && targetMaxAbs > 0 ? targetMaxAbs : 8000,
					maxGain: Number.isFinite(maxGain) && maxGain >= 1 ? maxGain : 8
				};
			},
			getGlassesVoiceStartChunks() {
				const value = Number(uni.getStorageSync('glassesVoiceStartChunks') || 3);
				return Number.isFinite(value) && value > 0 ? Math.max(1, Math.min(8, Math.round(value))) : 3;
			},
			shouldSendGlassesAudioLevel(level = {}) {
				const gate = this.getGlassesVoiceGate();
				return Number(level.maxAbs || 0) >= gate.minMaxAbs || Number(level.avgAbs || 0) >= gate.minAvgAbs;
			},
			shouldDropGlassesNearZeroAudio(level = {}) {
				const dropEnabled = uni.getStorageSync('glassesAudioDropSilent');
				if (!(dropEnabled === true || String(dropEnabled || '').toLowerCase() === 'true')) return false;
				const maxAbs = Number(level.maxAbs || 0);
				const avgAbs = Number(level.avgAbs || 0);
				const gate = this.getGlassesVoiceGate();
				const rawDropMaxAbs = uni.getStorageSync('glassesAudioDropMaxAbs');
				const rawDropAvgAbs = uni.getStorageSync('glassesAudioDropAvgAbs');
				const dropMaxAbs = rawDropMaxAbs === '' || rawDropMaxAbs === null || rawDropMaxAbs === undefined ?
					gate.minMaxAbs : Number(rawDropMaxAbs);
				const dropAvgAbs = rawDropAvgAbs === '' || rawDropAvgAbs === null || rawDropAvgAbs === undefined ?
					gate.minAvgAbs : Number(rawDropAvgAbs);
				const maxLimit = Number.isFinite(dropMaxAbs) && dropMaxAbs >= 0 ? dropMaxAbs : gate.minMaxAbs;
				const avgLimit = Number.isFinite(dropAvgAbs) && dropAvgAbs >= 0 ? dropAvgAbs : gate.minAvgAbs;
				return maxAbs <= maxLimit && avgAbs <= avgLimit;
			},
			isGlassesUploadGainEnabled() {
				const value = uni.getStorageSync('glassesAudioUploadGain');
				return value === true || String(value || '').toLowerCase() === 'true';
			},
			applyGlassesAudioGain(arrayBuffer, level = {}) {
				const normalized = this.normalizeArrayBuffer(arrayBuffer);
				if (!normalized || !normalized.byteLength) return normalized;
				if (!this.isGlassesUploadGainEnabled() || !this.shouldSendGlassesAudioLevel(level)) {
					this.lastGlassesAudioGain = 1;
					return normalized;
				}
				const maxAbs = Number(level.maxAbs || 0);
				const gate = this.getGlassesVoiceGate();
				if (!maxAbs || maxAbs >= gate.targetMaxAbs) {
					this.lastGlassesAudioGain = 1;
					return normalized;
				}
				const gain = Math.max(1, Math.min(gate.maxGain, gate.targetMaxAbs / maxAbs));
				this.lastGlassesAudioGain = Math.round(gain * 100) / 100;
				if (gain <= 1.05) return normalized;
				const source = new DataView(normalized);
				const output = new ArrayBuffer(normalized.byteLength);
				const target = new DataView(output);
				for (let i = 0; i + 1 < normalized.byteLength; i += 2) {
					let sample = source.getInt16(i, true);
					sample = Math.max(-32768, Math.min(32767, Math.round(sample * gain)));
					target.setInt16(i, sample, true);
				}
				return output;
			},
			getPcmLevelStats(arrayBuffer) {
				const normalized = this.normalizeArrayBuffer(arrayBuffer);
				if (!normalized || !normalized.byteLength) {
					return {
						bytes: 0,
						sampleCount: 0,
						avgAbs: 0,
						maxAbs: 0,
						nonZeroSamples: 0,
						nonZeroBytes: 0,
						silentLike: true,
						firstBytesHex: ''
					};
				}
				const bytes = new Uint8Array(normalized);
				const samples = Math.floor(bytes.byteLength / 2);
				let sumAbs = 0;
				let maxAbs = 0;
				let nonZeroSamples = 0;
				let nonZeroBytes = 0;
				for (let i = 0; i < bytes.byteLength; i += 1) {
					if (bytes[i] !== 0) nonZeroBytes += 1;
				}
				for (let i = 0; i + 1 < bytes.byteLength; i += 2) {
					let sample = bytes[i] | (bytes[i + 1] << 8);
					if (sample >= 0x8000) sample -= 0x10000;
					const abs = Math.abs(sample);
					sumAbs += abs;
					if (abs > maxAbs) maxAbs = abs;
					if (sample !== 0) nonZeroSamples += 1;
				}
				const avgAbs = samples > 0 ? Math.round(sumAbs * 100 / samples) / 100 : 0;
				const gate = this.getGlassesVoiceGate();
				return {
					bytes: bytes.byteLength,
					sampleCount: samples,
					avgAbs,
					maxAbs,
					nonZeroSamples,
					nonZeroBytes,
					silentLike: maxAbs < gate.minMaxAbs && avgAbs < gate.minAvgAbs,
					firstBytesHex: this.bytesToHex(bytes, 16)
				};
			},
			bytesToHex(bytes, limit = 16) {
				if (!bytes || !bytes.length) return '';
				const length = Math.min(bytes.length, limit);
				const parts = [];
				for (let i = 0; i < length; i += 1) {
					parts.push(Number(bytes[i] || 0).toString(16).padStart(2, '0'));
				}
				return parts.join('');
			},
			updateGlassesAudioLevel(level = {}) {
				this.lastGlassesAudioLevel = level;
				if (level.silentLike) this.glassesSilentChunks += 1;
				else this.glassesSilentChunks = 0;
				if (this.glassesSilentChunks >= 50 && this.glassesSilentChunks % 50 === 0) {
					console.log('Rokid audio continuous low level', {
						silentRun: this.glassesSilentChunks,
						maxAbs: level.maxAbs || 0,
						avgAbs: Number(level.avgAbs || 0).toFixed(1),
						nonZeroSamples: level.nonZeroSamples || 0
					});
				}
			},
			logGlassesAudioLevel(source, sequence, level = {}, event = {}) {
				if (!level || !level.bytes) return;
				if (sequence > 5 && sequence % 20 !== 0 && (!level.silentLike || this.glassesSilentChunks % 20 !== 0)) return;
				const nativeLevel = {
					avgAbs: event.avgAbs,
					maxAbs: event.maxAbs,
					nonZeroSamples: event.nonZeroSamples,
					silentLike: event.silentLike
				};
				console.log('Rokid audio chunk level', {
					source,
					sequence,
					codecType: event.audioCodecType !== undefined ? event.audioCodecType : this.glassesAudioCodecType,
					bytes: level.bytes,
					avgAbs: level.avgAbs,
					maxAbs: level.maxAbs,
					nonZeroSamples: level.nonZeroSamples,
					nonZeroBytes: level.nonZeroBytes,
					silentLike: level.silentLike,
					silentRun: this.glassesSilentChunks,
					firstBytesHex: level.firstBytesHex,
					nativeLevel
				});
			},
			sendGlassesSocketJson(payload) {
				if (!this.glassesSocketTask || !this.glassesSocketOpened) return;
				this.glassesSocketTask.send({
					data: JSON.stringify(payload)
				});
			},
			sendGlassesStopEvent() {
				return new Promise(resolve => {
					if (!this.glassesSocketTask || !this.glassesSocketOpened) {
						resolve(false);
						return;
					}
					let done = false;
					const finish = value => {
						if (done) return;
						done = true;
						resolve(value);
					};
					this.glassesSocketTask.send({
						data: JSON.stringify(this.buildGlassesSessionPayload('session.stop')),
						success: () => finish(true),
						fail: () => finish(false)
					});
					setTimeout(() => finish(false), 300);
				});
			},
			prepareGlassesWsAudioUpload() {
				this.glassesAudioUploadKey = this.createGlassesAudioUploadKey();
				this.glassesAudioChunkIndex = 0;
				this.glassesAudioSendQueue = [];
				this.glassesPcmPendingBytes = [];
				this.glassesPcmPendingSize = 0;
				this.glassesLastSocketSentAt = 0;
			},
			createGlassesAudioUploadKey() {
				const source = this.recordId || this.sessionId || this.deviceNo || this.getGlassesChannelId();
				const part = String(source || 'glasses').replace(/[^a-zA-Z0-9_-]/g, '-');
				return `glasses-${part}-${Date.now()}`;
			},
			getGlassesAudioUploadKey() {
				if (!this.glassesAudioUploadKey) this.glassesAudioUploadKey = this.createGlassesAudioUploadKey();
				return this.glassesAudioUploadKey;
			},
			getDeviceAudioRecordId() {
				const value = this.recordId || this.sessionId || '';
				const num = Number(value);
				return value !== '' && Number.isFinite(num) ? num : value;
			},
			getGlassesPcmChunkBytes() {
				return 16000 * 2 / 5;
			},
			getGlassesNativeUploadChunkBytes() {
				return 16000;
			},
			appendGlassesPcmBytes(arrayBuffer) {
				const normalized = this.normalizeArrayBuffer(arrayBuffer);
				if (!normalized || !normalized.byteLength) return;
				this.glassesPcmPendingBytes.push(new Uint8Array(normalized));
				this.glassesPcmPendingSize += normalized.byteLength;
				const chunkBytes = this.getGlassesPcmChunkBytes();
				while (this.glassesPcmPendingSize >= chunkBytes) {
					this.enqueueGlassesSocketAudioChunk(this.consumeGlassesPcmChunk(chunkBytes));
				}
			},
			consumeGlassesPcmChunk(size, pad = false) {
				const chunk = new Uint8Array(size);
				let offset = 0;
				while (offset < size && this.glassesPcmPendingBytes.length) {
					const head = this.glassesPcmPendingBytes[0];
					const take = Math.min(size - offset, head.byteLength);
					chunk.set(head.subarray(0, take), offset);
					offset += take;
					this.glassesPcmPendingSize -= take;
					if (take >= head.byteLength) {
						this.glassesPcmPendingBytes.shift();
					} else {
						this.glassesPcmPendingBytes[0] = head.subarray(take);
					}
				}
				if (!pad && offset < size) return chunk.slice(0, offset);
				return chunk;
			},
			flushGlassesPcmRemainder(pad = true) {
				if (!this.glassesPcmPendingSize) return;
				const chunk = this.consumeGlassesPcmChunk(this.getGlassesPcmChunkBytes(), pad);
				if (chunk && chunk.byteLength) this.enqueueGlassesSocketAudioChunk(chunk);
			},
			enqueueGlassesSocketAudioChunk(chunk) {
				const arrayBuffer = this.normalizeArrayBuffer(chunk);
				if (!arrayBuffer || !arrayBuffer.byteLength) return;
				if (!this.glassesSocketTask || !this.glassesSocketOpened) {
					this.glassesDroppedChunks += 1;
					this.scheduleGlassesAudioReconnect();
					return;
				}
				const bytes = arrayBuffer.byteLength || 0;
				const chunkIndex = this.glassesAudioChunkIndex + 1;
				const level = this.getPcmLevelStats(arrayBuffer);
				const chunkBase64 = this.arrayBufferToBase64(arrayBuffer);
				if (!chunkBase64) {
					this.glassesSocketSendFailed += 1;
					this.latestSnapshotError = '当前运行环境不支持 PCM 分片 Base64 编码';
					return;
				}
				const chunkData = Object.assign({}, this.buildGlassesSessionData('audio.chunk'), {
					type: 'audio.chunk',
					messageType: 'audio.chunk',
					chunkIndex,
					chunkSeq: chunkIndex,
					chunkBase64,
					bytes,
					chunkBytes: bytes,
					chunkDurationMs: Math.round(bytes / 2 / 16000 * 1000),
					pcmAvgAbs: level.avgAbs,
					pcmMaxAbs: level.maxAbs,
					pcmNonZeroSamples: level.nonZeroSamples,
					pcmNonZeroBytes: level.nonZeroBytes,
					pcmSilentLike: level.silentLike,
					pcmFirstBytesHex: level.firstBytesHex,
					pcmGain: this.lastGlassesAudioGain || 1
				});
				const payload = {
					event: 'audio.chunk',
					data: chunkData
				};
				this.glassesAudioChunkIndex = chunkIndex;
				this.glassesAudioSendQueue.push(payload);
				const maxQueueSize = 40;
				while (this.glassesAudioSendQueue.length > maxQueueSize) {
					this.glassesAudioSendQueue.shift();
					this.glassesDroppedChunks += 1;
				}
				this.pumpGlassesSocketAudioQueue();
			},
			pumpGlassesSocketAudioQueue() {
				if (this.glassesSocketSending) return;
				if (!this.glassesSocketTask || !this.glassesSocketOpened) return;
				const payload = this.glassesAudioSendQueue.shift();
				if (!payload) return;
				this.glassesSocketSending = true;
				const payloadData = payload.data || payload;
				const bytes = Number(payloadData.chunkBytes || payloadData.bytes || 0);
				const queueLength = this.glassesAudioSendQueue.length;
				let settled = false;
				const finishSend = (success, error) => {
					if (settled) return;
					settled = true;
					this.glassesSocketSending = false;
					if (success) {
						this.glassesLastSocketSentAt = Date.now();
						this.glassesSocketSentChunks += 1;
						this.glassesSocketSentBytes += bytes;
						if (this.glassesSocketSentChunks <= 3 || this.glassesSocketSentChunks % 20 === 0) {
							console.log('glasses pcm chunk sent', {
								sent: this.glassesSocketSentChunks,
								bytes,
								totalBytes: this.glassesSocketSentBytes,
								codecType: this.glassesAudioCodecType,
								maxAbs: payloadData.pcmMaxAbs,
								avgAbs: payloadData.pcmAvgAbs,
								gain: payloadData.pcmGain,
								silent: payloadData.pcmSilentLike,
								queue: this.glassesAudioSendQueue.length,
								drop: this.glassesDroppedChunks
							});
						}
					} else {
						this.glassesSocketSendFailed += 1;
						this.latestSnapshotError = this.formatErrorMessage(error, '眼镜音频分片发送失败');
						this.scheduleGlassesAudioReconnect();
					}
					this.pumpGlassesSocketAudioQueue();
				};
				const delay = Math.max(0, this.getGlassesSocketSendInterval(payloadData, queueLength) - (Date.now() - Number(
					this.glassesLastSocketSentAt || 0)));
				setTimeout(() => {
					if (this.glassesRuntimeStopping && payload.event === 'audio.chunk') {
						settled = true;
						this.glassesSocketSending = false;
						this.glassesDroppedChunks += 1;
						this.pumpGlassesSocketAudioQueue();
						return;
					}
					try {
						this.glassesSocketTask.send({
							data: JSON.stringify(payload),
							success: () => {
								finishSend(true);
							},
							fail: (error) => {
								finishSend(false, error);
							}
						});
						setTimeout(() => finishSend(true), 80);
					} catch (error) {
						finishSend(false, error);
					}
				}, delay);
			},
			getGlassesSocketSendInterval(payloadData = {}, queueLength = 0) {
				const bytes = Number(payloadData.chunkBytes || payloadData.bytes || 0);
				const explicitDuration = Number(payloadData.chunkDurationMs || 0);
				const inferredDuration = bytes > 0 ? Math.round(bytes / 2 / 16000 * 1000) : 0;
				let interval = explicitDuration > 0 ? explicitDuration : inferredDuration;
				if (!Number.isFinite(interval) || interval <= 0) interval = 200;
				if (queueLength >= 20) return 20;
				if (queueLength >= 10) return 50;
				if (queueLength >= 5) return 100;
				return Math.max(100, Math.min(250, interval));
			},
			drainGlassesAudioQueue(timeout = 2000) {
				return new Promise(resolve => {
					const startedAt = Date.now();
					const timer = setInterval(() => {
						if (!this.glassesAudioSendQueue.length && !this.glassesSocketSending) {
							clearInterval(timer);
							resolve(true);
							return;
						}
						if (Date.now() - startedAt >= timeout) {
							clearInterval(timer);
							resolve(false);
						}
					}, 50);
				});
			},
			normalizeArrayBuffer(value) {
				if (!value) return new ArrayBuffer(0);
				if (value instanceof ArrayBuffer) return value;
				if (ArrayBuffer.isView && ArrayBuffer.isView(value)) {
					return value.buffer.slice(value.byteOffset, value.byteOffset + value.byteLength);
				}
				if (value.buffer instanceof ArrayBuffer) return value.buffer;
				return new ArrayBuffer(0);
			},
			base64ToArrayBuffer(base64 = '') {
				const value = String(base64);
				if (!value) return new ArrayBuffer(0);
				if (typeof uni !== 'undefined' && typeof uni.base64ToArrayBuffer === 'function') return uni
					.base64ToArrayBuffer(value);
				const binary = typeof atob === 'function' ? atob(value) : '';
				const buffer = new ArrayBuffer(binary.length);
				const view = new Uint8Array(buffer);
				for (let i = 0; i < binary.length; i += 1) view[i] = binary.charCodeAt(i);
				return buffer;
			},
			arrayBufferToBase64(arrayBuffer) {
				const buffer = this.normalizeArrayBuffer(arrayBuffer);
				if (!buffer || !buffer.byteLength) return '';
				if (typeof uni !== 'undefined' && typeof uni.arrayBufferToBase64 === 'function') {
					return uni.arrayBufferToBase64(buffer);
				}
				const bytes = new Uint8Array(buffer);
				let binary = '';
				const chunkSize = 0x8000;
				for (let i = 0; i < bytes.length; i += chunkSize) {
					binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunkSize));
				}
				return typeof btoa === 'function' ? btoa(binary) : '';
			},
			async handleTeleprompterMessage(raw) {
				console.log('=rawrawrawrawraw===', raw);
				if (this.finishing || !this.isRunning || !this.teleprompterRunning) return;
				const payload = this.parseTeleprompterPayload(raw);
				const event = payload && payload.event;
				if (event === 'connected' || event === 'received' || event === 'binary.received') return;
				const data = payload && payload.data ? payload.data : payload;
				if (event === 'audio.transcript' || event === 'audio.transcript.finish') {
					const transcripts = data && Array.isArray(data.transcripts) ? data.transcripts : [];
					if (transcripts.length) this.mergeTranscriptList(transcripts);
					return;
				}
				const analysis = this.extractTeleprompterAnalysis(payload);
				if (analysis) this.applyAnalysis(analysis);
				if (!analysis) return;
				await this.updateGlassesTeleprompterFromScripts();
			},
			parseTeleprompterPayload(raw) {
				try {
					return typeof raw === 'string' ? JSON.parse(raw) : raw;
				} catch (error) {
					return raw;
				}
			},
			extractTeleprompterAnalysis(payload) {
				const data = payload && payload.data ? payload.data : payload;
				if (!data || typeof data !== 'object') return null;
				const explicit = data.analysis || data.report || data.currentAnalysisJson;
				if (explicit) return explicit;
				return this.hasAnalysisPayload(data) ? data : null;
			},
			hasAnalysisPayload(data = {}) {
				const keys = [
					'recommendedScripts',
					'speechExamples',
					'talkScripts',
					'scripts',
					'recommendScripts',
					'recommendedTalkScripts',
					'scriptRecommendations',
					'talkRecommendations',
					'recommendedSpeech',
					'recommendedWords',
					'suggestedScripts',
					'suggestions',
					'推荐话术',
					'话术建议',
					'沟通话术',
					'summary',
					'communicationSummary',
					'nextActions',
					'actions',
					'resistances',
					'objections',
					'blockers',
					'triggeredScenes',
					'scores',
					'extractedInfo'
				];
				return keys.some(key => data[key] !== undefined && data[key] !== null);
			},
			async updateGlassesTeleprompterFromScripts(force = false) {
				if (!this.isAiGlassesMode || this.finishing || !this.isRunning || !this.teleprompterRunning) return;
				const text = this.formatTeleprompterScripts();
				if (!text || (!force && text === this.lastTeleprompterScriptsText)) return;
				this.teleprompterText = text;
				this.lastTeleprompterScriptsText = text;
				await rokidGlass.updateCustomView({
					text,
					updateJson: this.buildCustomViewTextUpdateJson(text)
				});
			},
			formatTeleprompterScripts() {
				const rows = this.teleprompterScripts.slice(-2).map(item => String(item || '').trim()).filter(Boolean);
				return rows.map((item, index) => `${index + 1}. ${item}`).join('\n');
			},
			buildCustomViewTextUpdateJson(text) {
				return '[{"action":"update","id":"textView","props":{"text":"' + this.escapeJson(text) + '"}}]';
			},
			escapeJson(value = '') {
				return String(value).replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\n/g, '\\n').replace(/\r/g, '');
			},
			formatErrorMessage(error, fallback = '操作失败') {
				if (!error) return fallback;
				if (typeof error === 'string') return error;
				const nativeResult = error.nativeResult || {};
				const data = error.data || error.response || nativeResult.data || {};
				return error.responseMessage || error.message || error.errMsg || nativeResult.message || data
					.responseMessage || data.message || fallback;
			},
			uploadMediaFile(filePath) {
				return new Promise((resolve, reject) => {
					if (!filePath) {
						reject(new Error('filePath is required'));
						return;
					}
					uni.uploadFile({
						url: this.getApiBase() + '/system/file/uploadFile',
						filePath,
						name: 'file',
						header: this.getSocketHeader(),
						formData: Object.assign({}, this.glassesDevicePayload(), {
							sessionId: this.sessionId,
							recordId: this.recordId || '',
							deviceNo: this.deviceNo,
							deviceType: 'AI_GLASSES'
						}),
						success: (res) => {
							let data = res.data;
							if (typeof data === 'string') {
								try {
									data = JSON.parse(data);
								} catch (error) {}
							}
							resolve({
								statusCode: res.statusCode,
								response: data,
								url: data && data.data ? data.data.url : (data && data.url ?
									data.url : '')
							});
						},
						fail: reject
					});
				});
			},
			loadStatus() {
				if (!this.sessionId) return;
				this.$u.api.aiSceneGetSessionStatus({
					sessionId: this.sessionId
				}).then(res => {
					const data = (res && res.data) || {};
					this.applyRuntimeStatus(data);
					if (data.currentAnalysisJson) {
						this.applyAnalysis(data.currentAnalysisJson);
					}
					if (data.latestSnapshotError) {
						this.latestSnapshotError = data.latestSnapshotError;
					}
					if (data.recentTranscripts && data.recentTranscripts.length) {
						this.mergeTranscriptList(data.recentTranscripts);
					}
					if (this.isCompleted || this.isFailed) {
						this.finishing = false;
						this.stopPolling();
					}
				}).catch(err => {
					console.log(err);
				});
			},
			applyRuntimeStatus(data = {}) {
				if (this.localAiGlassesStartFailed) {
					this.status = STATUS_FAILED;
					return;
				}
				if (data.status !== undefined && data.status !== null) this.status = data.status;
				if (data.reportReady !== undefined) this.reportReady = !!data.reportReady;
				if (data.transcriptCount !== undefined) this.transcriptCount = Number(data.transcriptCount || 0);
				if (data.maxSegmentSeq !== undefined) this.maxSegmentSeq = Number(data.maxSegmentSeq || 0);
				if (Array.isArray(data.businessObjects)) this.businessObjects = data.businessObjects;
				this.syncClock(data);
				this.updateDuration();
			},
			syncClock(data = {}) {
				const serverNow = this.parseTime(data.serverNow);
				if (serverNow) {
					this.serverOffsetMs = Date.now() - serverNow;
				}
				const startedAt = this.parseTime(data.startedAt);
				const endedAt = this.parseTime(data.endedAt);
				if (startedAt) this.startedAtMs = startedAt;
				if (endedAt) this.endedAtMs = endedAt;
				if ((this.isFinishing || this.isCompleted || this.isFailed) && !this.endedAtMs) {
					this.endedAtMs = this.currentServerMs();
				}
				if (data.durationSeconds !== undefined && data.durationSeconds !== null && (!this.isRunning || !this
						.startedAtMs)) {
					this.displayDurationSeconds = Number(data.durationSeconds || 0);
				}
			},
			updateDuration() {
				if (!this.startedAtMs) return;
				if (this.isRunning) {
					this.displayDurationSeconds = Math.max(0, Math.floor((this.currentServerMs() - this.startedAtMs) /
						1000));
					return;
				}
				const end = this.endedAtMs || this.startedAtMs + Number(this.displayDurationSeconds || 0) * 1000;
				this.displayDurationSeconds = Math.max(0, Math.floor((end - this.startedAtMs) / 1000));
			},
			currentServerMs() {
				return Date.now() - Number(this.serverOffsetMs || 0);
			},
			parseTime(value) {
				if (!value) return 0;
				if (typeof value === 'number') return value;
				if (value instanceof Date) return value.getTime();
				const raw = String(value);
				const direct = new Date(raw).getTime();
				if (!isNaN(direct)) return direct;
				const text = raw.replace(/-/g, '/');
				const time = new Date(text).getTime();
				return isNaN(time) ? 0 : time;
			},
			mergeTranscriptList(list = []) {
				list.forEach(item => this.mergeTranscript(item, false));
				this.sortTranscriptList();
			},
			mergeTranscript(data = {}, partial = false) {
				const text = data.text || data.transcript || '';
				if (!text) return;
				const segmentSeq = data.segmentSeq !== undefined && data.segmentSeq !== null ? Number(data.segmentSeq) :
					null;
				const key = segmentSeq ? `seq-${segmentSeq}` : (partial ? 'partial-current' : `local-${Date.now()}`);
				const item = {
					key,
					segmentSeq,
					text,
					final: !partial && data.final !== false,
					startOffsetMs: data.startOffsetMs,
					endOffsetMs: data.endOffsetMs,
					timeText: this.transcriptTimeText(data)
				};
				if (item.final) {
					this.transcriptList = this.transcriptList.filter(row => row.key !== 'partial-current');
				}
				const index = this.transcriptList.findIndex(row => row.key === key || (segmentSeq && row.segmentSeq ===
					segmentSeq));
				if (index >= 0) {
					this.$set(this.transcriptList, index, Object.assign({}, this.transcriptList[index], item));
				} else {
					this.transcriptList.push(item);
				}
				this.sortTranscriptList();
				if (this.transcriptList.length > 80) {
					this.transcriptList = this.transcriptList.slice(-80);
				}
			},
			sortTranscriptList() {
				this.transcriptList = this.transcriptList.slice().sort((a, b) => {
					if (a.segmentSeq && b.segmentSeq) return a.segmentSeq - b.segmentSeq;
					if (a.segmentSeq) return -1;
					if (b.segmentSeq) return 1;
					return 0;
				});
			},
			transcriptTimeText(data = {}) {
				if (data.startOffsetMs || data.endOffsetMs) {
					return this.msText(data.startOffsetMs) + '-' + this.msText(data.endOffsetMs);
				}
				if (data.receiveTime) {
					return this.shortTime(data.receiveTime);
				}
				return this.shortTime(Date.now());
			},
			msText(value) {
				const seconds = Math.max(0, Math.floor(Number(value || 0) / 1000));
				const m = Math.floor(seconds / 60);
				const s = seconds % 60;
				return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
			},
			shortTime(value) {
				const time = this.parseTime(value) || Date.now();
				const date = new Date(time);
				return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
			},
			applyAnalysis(input) {
				const parsed = this.parseJson(input);
				if (!parsed || typeof parsed !== 'object') return;
				this.analysis = Object.assign({}, this.analysis, parsed);
				this.updateGlassesTeleprompterFromScripts().catch(() => {});
			},
			parseJson(value) {
				if (!value) return {};
				if (typeof value === 'object') return value;
				try {
					return JSON.parse(value);
				} catch (e) {
					return {};
				}
			},
			arrayFrom(value) {
				if (!value) return [];
				if (Array.isArray(value)) return value;
				if (typeof value === 'object') return Object.keys(value).map(key => value[key]);
				return [value];
			},
			recommendedScriptSource() {
				const candidates = [
					this.analysis.recommendedScripts,
					this.analysis.speechExamples,
					this.analysis.talkScripts,
					this.analysis.scripts,
					this.analysis.recommendScripts,
					this.analysis.recommendedTalkScripts,
					this.analysis.scriptRecommendations,
					this.analysis.talkRecommendations,
					this.analysis.recommendedSpeech,
					this.analysis.recommendedWords,
					this.analysis.suggestedScripts,
					this.analysis.suggestions,
					this.analysis['推荐话术'],
					this.analysis['话术建议'],
					this.analysis['沟通话术']
				];
				return candidates.find(item => this.arrayFrom(item)
					.map(row => this.valueToText(row))
					.some(text => String(text || '').trim())) || [];
			},
			realtimeList(value, limit) {
				const rows = this.arrayFrom(value).map(item => this.valueToText(item)).filter(Boolean);
				if (rows.length <= limit) return rows;
				return rows.slice(-limit);
			},
			realtimeScriptList(value, limit) {
				const rows = this.arrayFrom(value)
					.map(item => this.valueToText(item))
					.filter(Boolean)
					.filter(text => !this.isOpeningScript(text));
				if (rows.length <= limit) return rows;
				return rows.slice(-limit);
			},
			isOpeningScript(text) {
				const value = String(text || '');
				return /破冰话术|开场白|首次致电|初次致电|自我介绍|我是.*(经纪人|顾问|找房|中介)|今天给您打电话.*(了解|确认)/.test(value);
			},
			valueToText(value) {
				if (value === null || value === undefined) return '';
				if (typeof value === 'string' || typeof value === 'number') return String(value);
				if (typeof value === 'boolean') return value ? '是' : '否';
				if (Array.isArray(value)) return value.map(item => this.valueToText(item)).filter(Boolean).join('、');
				if (typeof value === 'object') {
					const content = this.firstText(value, ['content', 'description', 'text', 'value', 'reason', 'summary',
						'name'
					]);
					const tags = ['type', 'priority', 'intensity', 'level', 'sceneName'].map(key => this.firstText(value, [
						key
					])).filter(
						Boolean);
					const used = ['content', 'description', 'text', 'value', 'reason', 'summary', 'name', 'type',
						'priority', 'intensity', 'level', 'sceneName'
					];
					const detail = Object.keys(value).filter(key => used.indexOf(key) < 0 && value[key] !== undefined &&
							value[key] !== null)
						.map(key => `${this.labelForKey(key)}：${this.valueToText(value[key])}`)
						.filter(Boolean).join('；');
					if (content) {
						return `${tags.length ? '【' + tags.join('｜') + '】' : ''}${content}${detail ? '；' + detail : ''}`;
					}
					return Object.keys(value).map(key => `${this.labelForKey(key)}：${this.valueToText(value[key])}`).join(
						'；');
				}
				return String(value);
			},
			firstText(source, keys) {
				for (let i = 0; i < keys.length; i += 1) {
					const value = source[keys[i]];
					if (value !== undefined && value !== null && String(value).trim() !== '') {
						return this.valueToText(value);
					}
				}
				return '';
			},
			labelForKey(key) {
				const rawKey = String(key || '').trim();
				if (FIELD_LABELS[rawKey]) return FIELD_LABELS[rawKey];
				const normalizedKey = this.normalizedKey(rawKey);
				if (FIELD_LABEL_ALIASES[normalizedKey]) return FIELD_LABEL_ALIASES[normalizedKey];
				Object.keys(FIELD_LABELS).some(itemKey => {
					if (this.normalizedKey(itemKey) === normalizedKey) {
						key = itemKey;
						return true;
					}
					return false;
				});
				if (FIELD_LABELS[key]) return FIELD_LABELS[key];
				if (/[\u4e00-\u9fa5]/.test(rawKey)) return rawKey;
				const isMeeting = this.sceneCategory == 3;
				const isChannel = this.sceneCategory == 4;
				if (isChannel && /promotion|promote|house|listing|property/i.test(rawKey)) return '推广房源';
				if (isChannel && /channel|store|broker|peer/i.test(rawKey)) return '渠道反馈';
				if (isChannel && /cooperation|intent/i.test(rawKey)) return '合作意向';
				if (isChannel && /lead|customer/i.test(rawKey)) return '客户线索';
				if (isChannel && /objection|resistance|risk/i.test(rawKey)) return '推广异议';
				if (isChannel && /follow|action|coordination/i.test(rawKey)) return '协同动作';
				if (isMeeting && /meeting|agenda|topic/i.test(rawKey)) return '会议主题';
				if (isMeeting && /decision/i.test(rawKey)) return '关键决策';
				if (isMeeting && /todo|task|actionItem|action_item/i.test(rawKey)) return '待办事项';
				if (isMeeting && /owner|responsible|assignee/i.test(rawKey)) return '责任人';
				if (isMeeting && /deadline|due/i.test(rawKey)) return '截止时间';
				if (isMeeting && /risk|blocker|problem|issue|control/i.test(rawKey)) return '问题风险';
				if (isMeeting && /team|mood|emotion/i.test(rawKey)) return '团队状态';
				if (isMeeting && /target|performance/i.test(rawKey)) return '业绩目标';
				if (isMeeting && /training|coach|newcomer/i.test(rawKey)) return '培训/带教';
				if (isMeeting && /review|conclusion/i.test(rawKey)) return '复盘结论';
				if (isMeeting && /follow/i.test(rawKey)) return '跟进事项';
				if (isMeeting && /values|culture/i.test(rawKey)) return '价值观';
				if (/price|pricing|fee|cost|budget|money|loan|payment|commission/i.test(rawKey)) return '价格/资金';
				if (/owner.*profile|profile.*owner/i.test(rawKey)) return '业主情况';
				if (/single.*agent|single.*agency|mls|citywide/i.test(rawKey)) return '单边代理/联卖';
				if (/trust|relation/i.test(rawKey)) return '信任关系';
				if (/time|urgent|cycle/i.test(rawKey)) return '时间节奏';
				if (/show|view|feedback/i.test(rawKey)) return '看房反馈';
				if (/market/i.test(rawKey)) return '市场态度';
				if (/reason|motivation/i.test(rawKey)) return '动机原因';
				if (/strategy|action|next|suggestion|follow/i.test(rawKey)) return '跟进策略';
				if (/preference|match|house|property/i.test(rawKey)) return '房源偏好';
				if (/resistance|objection|risk|blocker/i.test(rawKey)) return '抗性/阻力';
				if (/intention|intent/i.test(rawKey)) return '成交意向';
				if (/clarity/i.test(rawKey)) return '清晰度';
				if (/recognition|understanding|acceptance/i.test(rawKey)) return '认知程度';
				return this.readableKeyFallback(rawKey);
			},
			normalizedKey(key) {
				return String(key || '')
					.replace(/([a-z])([A-Z])/g, '$1_$2')
					.replace(/[^a-zA-Z0-9\u4e00-\u9fa5]+/g, '')
					.toLowerCase();
			},
			readableKeyFallback(key) {
				const value = String(key || '').trim();
				if (!value) return '其他信息';
				if (/[\u4e00-\u9fa5]/.test(value)) return value;
				return value
					.replace(/([a-z])([A-Z])/g, '$1 $2')
					.replace(/[_-]+/g, ' ')
					.replace(/\s+/g, ' ')
					.trim();
			},
			clampScore(value) {
				const num = Number(value);
				if (!isFinite(num)) return 0;
				return Math.max(0, Math.min(100, Math.round(num)));
			},
			copyText(text) {
				uni.setClipboardData({
					data: text,
					success: () => {
						uni.showToast({
							title: '已复制',
							icon: 'none'
						});
					}
				});
			},
			endScene() {
				if (this.finishing || !this.canEndScene) return;
				uni.showModal({
					title: '结束AI场景',
					content: '结束后将停止本次录音，并生成最终分析报告。',
					cancelText: '继续录音',
					confirmText: '结束',
					success: res => {
						if (res.confirm) this.finishScene();
					}
				});
			},
			finishScene() {
				this.finishing = true;
				const stopRuntime = this.isAiGlassesMode ? this.stopAiGlassesRuntime() : Promise.resolve();
				const request = stopRuntime.then(() => {
					return this.isAiGlassesMode ? this.$u.api.aiSceneFinishSession({
						sessionId: this.sessionId,
						deviceNo: this.deviceNo,
						deviceType: 'AI_GLASSES'
					}) : (this.deviceNo && !this.isUnclaimedMode ? this.$u.api.dudutalkAudioStop({
						deviceNo: this.deviceNo
					}) : this.$u.api.aiSceneFinishSession({
						sessionId: this.sessionId
					}));
				});
				request.then(res => {
					const data = (res && res.data) || {};
					if (data.aiSceneSessionId && !this.sessionId) this.sessionId = data.aiSceneSessionId;
					this.status = STATUS_FINISHING;
					this.endedAtMs = this.endedAtMs || this.currentServerMs();
					this.updateDuration();
					this.closeRealtimeSocket();
					this.startPolling();
					uni.showToast({
						title: '已结束，正在生成报告',
						icon: 'none'
					});
				}).catch(err => {
					console.log(err);
					this.finishing = false;
					uni.showToast({
						title: '结束失败，请重试',
						icon: 'none'
					});
				});
			},
			async stopAiGlassesRuntime() {
				const shouldStopNativeAudio = this.teleprompterRunning || this.glassesState.audioStarted || this
					.glassesAudioSequence >
					0;
				this.glassesRuntimeStopping = true;
				this.teleprompterRunning = false;
				this.glassesState = Object.assign({}, this.glassesState, {
					audioStarted: false
				});
				this.glassesAudioSendQueue = [];
				this.glassesPcmPendingBytes = [];
				this.glassesPcmPendingSize = 0;
				await this.sendGlassesStopEvent();
				this.closeGlassesAudioSocket();
				try {
					if (shouldStopNativeAudio) {
						const res = await rokidGlass.stopAudioReady({
							type: 'test',
							iosRecordType: 'test',
							recordType: 'test'
						});
						this.mergeGlassesState(res);
						this.glassesAudioPath = res.path || res.pcmPath || this.glassesAudioPath;
						if (this.glassesAudioPath) {
							const upload = await this.uploadMediaFile(this.glassesAudioPath);
							this.glassesAudioUrl = upload && upload.url ? upload.url : '';
						}
					}
				} catch (error) {
					this.latestSnapshotError = this.formatErrorMessage(error, '眼镜录音停止失败');
				} finally {
					this.teleprompterRunning = false;
					this.closeGlassesAudioSocket();
					this.unbindRokidEvents();
					if (rokidGlass.clearEventHandlers) rokidGlass.clearEventHandlers();
					try {
						await rokidGlass.closeCustomView();
					} catch (error) {}
					if (rokidGlass.release) {
						try {
							await rokidGlass.release();
						} catch (error) {}
					}
				}
			},
			openSyncConfirm() {
				if (!this.sessionId) return;
				uni.navigateTo({
					url: `/pages/smartIDBadge/syncConfirm?sessionId=${encodeURIComponent(this.sessionId)}`
				});
			},
			viewReport() {
				if (!this.sessionId) return;
				uni.showLoading({
					title: '打开报告'
				});
				this.$u.api.aiSceneCreateReportTicket({
					sessionId: this.sessionId
				}).then(res => {
					const data = (res && res.data) || {};
					const ticket = data.ticket || '';
					const url = this.buildReportUrl(this.sessionId, ticket);
					uni.navigateTo({
						url: `/pages/smartIDBadge/historicalReport?sessionId=${encodeURIComponent(this.sessionId)}&ticket=${encodeURIComponent(ticket)}&url=${encodeURIComponent(url)}`
					});
				}).catch(err => {
					console.log(err);
					uni.showToast({
						title: '报告生成中，请稍后刷新',
						icon: 'none'
					});
				}).finally(() => {
					uni.hideLoading();
				});
			},
			buildReportUrl(sessionId, ticket = '') {
				const configured = uni.getStorageSync('aiSceneReportUrl') ||
					'https://www.tcwang.cc/funny/AISenceReport/index.html';
				const apiBase = this.getApiBase();
				let url =
					`${configured}${configured.indexOf('?') > -1 ? '&' : '?'}sessionId=${encodeURIComponent(sessionId)}`;
				if (ticket) url += `&ticket=${encodeURIComponent(ticket)}`;
				if (apiBase) url += `&apiBase=${encodeURIComponent(apiBase)}`;
				url +=
					`&syncUrl=${encodeURIComponent(`/pages/smartIDBadge/syncConfirm?sessionId=${encodeURIComponent(sessionId)}`)}`;
				return url;
			}
		}
	}
</script>

<style lang="scss" scoped>
	.scene-page {
		min-height: 100vh;
		background: #f4f7fa;
		color: #182232;
	}

	.status-card {
		margin: 16rpx 24rpx 0;
		padding: 24rpx;
		border-radius: 18rpx;
		color: #fff;
		background: linear-gradient(135deg, #102d3f 0%, #0c5260 100%);
		box-shadow: 0 14rpx 32rpx rgba(12, 43, 62, 0.14);

		&.finishing {
			background: linear-gradient(135deg, #39404d 0%, #6d5b29 100%);
		}

		&.completed {
			background: linear-gradient(135deg, #113b2c 0%, #0a7a5b 100%);
		}

		&.failed {
			background: linear-gradient(135deg, #4b2228 0%, #82333c 100%);
		}
	}

	.status-top {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 20rpx;
	}

	.scene-title {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;

		.title {
			font-size: 34rpx;
			line-height: 44rpx;
			font-weight: 800;
		}

		.sub {
			margin-top: 6rpx;
			font-size: 24rpx;
			line-height: 32rpx;
			color: rgba(255, 255, 255, 0.72);
		}
	}

	.status-pill {
		display: flex;
		align-items: center;
		height: 46rpx;
		padding: 0 18rpx;
		border-radius: 999rpx;
		background: rgba(255, 255, 255, 0.12);
		font-size: 23rpx;
		font-weight: 700;
		white-space: nowrap;
	}

	.status-dot {
		width: 10rpx;
		height: 10rpx;
		margin-right: 10rpx;
		border-radius: 50%;
		background: #33e59d;
	}

	.runtime-row {
		display: flex;
		align-items: flex-end;
		justify-content: space-between;
		gap: 24rpx;
		margin-top: 24rpx;
	}

	.timer {
		display: flex;
		flex-direction: column;

		.time {
			font-size: 58rpx;
			line-height: 66rpx;
			font-weight: 800;
			letter-spacing: 0;
		}

		.time-label {
			margin-top: 4rpx;
			font-size: 22rpx;
			color: rgba(255, 255, 255, 0.68);
		}
	}

	.runtime-meta {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: flex-end;
		gap: 6rpx;
		font-size: 22rpx;
		color: rgba(255, 255, 255, 0.76);
		text-align: right;
	}

	.warning-line {
		margin-top: 18rpx;
		padding: 14rpx 18rpx;
		border-radius: 12rpx;
		background: rgba(255, 255, 255, 0.12);
		font-size: 23rpx;
		line-height: 34rpx;

		&.error {
			background: rgba(255, 90, 90, 0.18);
		}
	}

	.content-scroll {
		height: calc(100vh - 246rpx);
		padding: 0 24rpx;
		box-sizing: border-box;
	}

	.ai-live-card,
	.section-card {
		margin-top: 18rpx;
		padding: 24rpx;
		border-radius: 18rpx;
		background: #fff;
		border: 1rpx solid #e7edf3;
		box-shadow: 0 8rpx 24rpx rgba(25, 43, 70, 0.04);
	}

	.ai-live-card {
		border-color: rgba(0, 166, 206, 0.28);
		background: linear-gradient(180deg, #ffffff 0%, #f7fdfd 100%);
	}

	.section-head {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 16rpx;
		margin-bottom: 18rpx;

		&.compact {
			margin-bottom: 14rpx;
		}
	}

	.section-title {
		display: block;
		font-size: 32rpx;
		line-height: 42rpx;
		font-weight: 800;
		color: #152033;
	}

	.section-sub {
		display: block;
		margin-top: 2rpx;
		font-size: 23rpx;
		line-height: 32rpx;
		color: #7b8797;
	}

	.refresh {
		font-size: 24rpx;
		color: #009b9a;
		white-space: nowrap;
	}

	.empty-block {
		padding: 32rpx 20rpx;
		border-radius: 14rpx;
		background: #f5f8fb;
		text-align: center;

		&.small {
			padding: 24rpx 16rpx;
		}
	}

	.empty-title {
		display: block;
		font-size: 28rpx;
		font-weight: 700;
		color: #354052;
	}

	.empty-sub {
		display: block;
		margin-top: 6rpx;
		font-size: 24rpx;
		line-height: 34rpx;
		color: #8a94a6;
	}

	.block-title,
	.quick-title {
		font-size: 25rpx;
		font-weight: 800;
		color: #202b3c;
		margin-bottom: 12rpx;
	}

	.script-item {
		display: flex;
		align-items: flex-start;
		gap: 16rpx;
		padding: 18rpx;
		border-radius: 14rpx;
		background: #fff8ed;
		border: 1rpx solid #ffe4b8;
		margin-bottom: 12rpx;
	}

	.script-text {
		flex: 1;
		font-size: 28rpx;
		line-height: 42rpx;
		font-weight: 650;
		color: #382a1a;
	}

	.copy-btn {
		color: #d88400;
		font-size: 24rpx;
		font-weight: 700;
		padding-top: 4rpx;
	}

	.quick-grid {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 14rpx;
		margin-top: 16rpx;
	}

	.quick-box {
		min-height: 132rpx;
		padding: 18rpx;
		border-radius: 14rpx;
		background: #f2fbf7;
		border: 1rpx solid #d9f1e8;

		&.resistance {
			background: #fff7ef;
			border-color: #ffe3c7;
		}
	}

	.quick-line {
		font-size: 25rpx;
		line-height: 36rpx;
		color: #344255;
		margin-top: 8rpx;
	}

	.score-grid {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 14rpx;
	}

	.score-item {
		padding: 18rpx;
		border-radius: 14rpx;
		background: #f7fafc;
	}

	.score-top {
		display: flex;
		align-items: center;
		justify-content: space-between;
		font-size: 24rpx;
		color: #4c596b;
	}

	.score-num {
		font-size: 34rpx;
		font-weight: 800;
		color: #008f8b;
	}

	.score-bar {
		height: 10rpx;
		margin-top: 12rpx;
		border-radius: 999rpx;
		background: #dfe8f0;
		overflow: hidden;
	}

	.score-fill {
		height: 100%;
		border-radius: 999rpx;
		background: linear-gradient(90deg, #12b7a9, #2f7cf6);
	}

	.scene-chip-list {
		display: flex;
		flex-wrap: wrap;
		gap: 12rpx;
		margin-top: 18rpx;
	}

	.scene-chip {
		display: flex;
		align-items: center;
		gap: 8rpx;
		padding: 10rpx 14rpx;
		border-radius: 999rpx;
		background: #eefafa;
		color: #087d7b;
		font-size: 23rpx;
		font-weight: 700;
	}

	.confidence {
		color: #d08a00;
	}

	.info-list {
		margin-top: 18rpx;
		border-top: 1rpx solid #eef2f5;
	}

	.info-row {
		display: flex;
		gap: 18rpx;
		padding: 16rpx 0;
		border-bottom: 1rpx solid #eef2f5;
	}

	.info-label {
		width: 150rpx;
		flex-shrink: 0;
		font-size: 24rpx;
		color: #00928e;
		font-weight: 800;
	}

	.info-value {
		flex: 1;
		font-size: 25rpx;
		line-height: 38rpx;
		color: #2d3748;
	}

	.summary-text {
		display: block;
		font-size: 27rpx;
		line-height: 42rpx;
		color: #2d3748;
	}

	.live-transcript {
		display: flex;
		align-items: flex-start;
		gap: 14rpx;
		margin-bottom: 14rpx;
		padding: 18rpx;
		border-radius: 14rpx;
		background: #eefafa;
		border: 1rpx solid #cdeeed;

		&.panel-live {
			margin-bottom: 16rpx;
		}
	}

	.live-dot {
		width: 14rpx;
		height: 14rpx;
		margin-top: 12rpx;
		border-radius: 50%;
		background: #12b7a9;
		box-shadow: 0 0 0 8rpx rgba(18, 183, 169, 0.12);
		flex-shrink: 0;
	}

	.live-content {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 4rpx;
	}

	.live-label {
		font-size: 22rpx;
		line-height: 30rpx;
		font-weight: 800;
		color: #087d7b;
	}

	.live-text {
		font-size: 27rpx;
		line-height: 40rpx;
		color: #253144;
	}

	.transcript-list {
		display: flex;
		flex-direction: column;
		gap: 12rpx;
	}

	.transcript-item {
		padding: 18rpx;
		border-radius: 14rpx;
		background: #f7fafc;
	}

	.transcript-meta {
		display: flex;
		justify-content: space-between;
		margin-bottom: 8rpx;
		font-size: 22rpx;
		color: #8290a3;
	}

	.transcript-text {
		font-size: 26rpx;
		line-height: 40rpx;
		color: #253144;
	}

	.bottom-space {
		height: 156rpx;
	}

	.footer-bar {
		position: fixed;
		left: 0;
		right: 0;
		bottom: 0;
		z-index: 50;
		display: flex;
		align-items: center;
		gap: 16rpx;
		padding: 18rpx 24rpx;
		padding-bottom: calc(18rpx + env(safe-area-inset-bottom));
		background: #fff;
		box-shadow: 0 -8rpx 24rpx rgba(20, 36, 56, 0.08);
	}

	.footer-hint {
		flex: 1;
		font-size: 24rpx;
		line-height: 34rpx;
		color: #6c7788;
	}

	.footer-actions {
		display: flex;
		align-items: center;
		gap: 12rpx;
	}

	.footer-btn {
		width: 216rpx;
		height: 76rpx;
		border-radius: 12rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 28rpx;
		font-weight: 800;

		&.danger {
			background: #e84b4b;
			color: #fff;
		}

		&.primary {
			background: #08a39c;
			color: #fff;
		}

		&.ghost {
			background: #edf5f7;
			color: #087d7b;
		}

		&.compact {
			width: 168rpx;
		}

		&.disabled {
			opacity: 0.62;
		}
	}

	.transcript-mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		z-index: 300;
		background: rgba(0, 0, 0, 0.42);
		display: flex;
		align-items: flex-end;
	}

	.transcript-panel {
		width: 100%;
		max-height: 78vh;
		background: #fff;
		border-radius: 28rpx 28rpx 0 0;
		padding: 28rpx;
		box-sizing: border-box;
	}

	.transcript-panel-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		font-size: 32rpx;
		font-weight: 800;
		color: #182232;
		margin-bottom: 18rpx;

		.close {
			font-size: 26rpx;
			color: #009b9a;
			font-weight: 600;
		}
	}

	.transcript-panel-scroll {
		height: 62vh;
	}

	.panel-item {
		margin-bottom: 14rpx;
	}
</style>
