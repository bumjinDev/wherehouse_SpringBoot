import React, { useState } from 'react';

const CheckpointArchitecture = () => {
  const [step, setStep] = useState(0);
  
  const steps = [
    {
      title: "1단계: 초기 상태",
      description: "애플리케이션이 UPDATE 쿼리를 실행하기 전 상태. Buffer Cache와 Data Files가 동일한 데이터를 가지고 있다.",
      bufferCache: [
        { id: 'A', value: '100', dirty: false, scn: null },
        { id: 'B', value: '200', dirty: false, scn: null },
        { id: 'C', value: '300', dirty: false, scn: null },
      ],
      redoLogBuffer: [],
      dataFiles: [
        { id: 'A', value: '100' },
        { id: 'B', value: '200' },
        { id: 'C', value: '300' },
      ],
      redoLogFiles: [],
      checkpointPosition: null,
      activeFlow: null,
      committed: false,
    },
    {
      title: "2단계: UPDATE 실행",
      description: "UPDATE로 Block A의 값을 100→150으로 변경. 변경은 Buffer Cache에만 반영되고, 동시에 Redo Log Buffer에 변경 이력이 기록된다. Data Files는 아직 변경되지 않음.",
      bufferCache: [
        { id: 'A', value: '150', dirty: true, scn: 1000 },
        { id: 'B', value: '200', dirty: false, scn: null },
        { id: 'C', value: '300', dirty: false, scn: null },
      ],
      redoLogBuffer: [
        { scn: 1000, change: 'A: 100→150' }
      ],
      dataFiles: [
        { id: 'A', value: '100' },
        { id: 'B', value: '200' },
        { id: 'C', value: '300' },
      ],
      redoLogFiles: [],
      checkpointPosition: null,
      activeFlow: 'update',
      committed: false,
    },
    {
      title: "3단계: 추가 UPDATE 실행",
      description: "Block B도 UPDATE (200→250). 이제 Buffer Cache에 Dirty Buffer가 2개 존재한다. 여전히 Data Files는 변경 전 상태.",
      bufferCache: [
        { id: 'A', value: '150', dirty: true, scn: 1000 },
        { id: 'B', value: '250', dirty: true, scn: 1050 },
        { id: 'C', value: '300', dirty: false, scn: null },
      ],
      redoLogBuffer: [
        { scn: 1000, change: 'A: 100→150' },
        { scn: 1050, change: 'B: 200→250' }
      ],
      dataFiles: [
        { id: 'A', value: '100' },
        { id: 'B', value: '200' },
        { id: 'C', value: '300' },
      ],
      redoLogFiles: [],
      checkpointPosition: null,
      activeFlow: 'update',
      committed: false,
    },
    {
      title: "4단계: COMMIT 실행",
      description: "COMMIT하면 LGWR가 Redo Log Buffer → Redo Log Files로 기록한다. 이것이 '커밋 완료'의 조건이다. 주목: Buffer Cache의 Dirty Buffer는 여전히 메모리에만 있고, Data Files는 변경되지 않았다!",
      bufferCache: [
        { id: 'A', value: '150', dirty: true, scn: 1000 },
        { id: 'B', value: '250', dirty: true, scn: 1050 },
        { id: 'C', value: '300', dirty: false, scn: null },
      ],
      redoLogBuffer: [],
      dataFiles: [
        { id: 'A', value: '100' },
        { id: 'B', value: '200' },
        { id: 'C', value: '300' },
      ],
      redoLogFiles: [
        { scn: 1000, change: 'A: 100→150' },
        { scn: 1050, change: 'B: 200→250' }
      ],
      checkpointPosition: null,
      activeFlow: 'commit',
      committed: true,
    },
    {
      title: "5단계: 이 시점에서 장애 발생하면?",
      description: "서버 크래시! Buffer Cache(메모리)가 날아갔다. Data Files에는 아직 옛날 값(100, 200)이 있다. 하지만 Redo Log Files에 변경 이력이 있으므로 복구 가능. 단, Checkpoint Position이 없으면 어디서부터 복구할지 모른다.",
      bufferCache: [
        { id: '?', value: '???', dirty: false, scn: null },
        { id: '?', value: '???', dirty: false, scn: null },
        { id: '?', value: '???', dirty: false, scn: null },
      ],
      dataFiles: [
        { id: 'A', value: '100' },
        { id: 'B', value: '200' },
        { id: 'C', value: '300' },
      ],
      redoLogFiles: [
        { scn: 1000, change: 'A: 100→150' },
        { scn: 1050, change: 'B: 200→250' }
      ],
      checkpointPosition: null,
      activeFlow: 'crash',
      crashed: true,
    },
    {
      title: "6단계: 정상 운영 - Checkpoint 발생 전",
      description: "장애 시나리오를 리셋하고, 정상 운영 상태로 돌아가자. COMMIT 완료 후 상태에서 Checkpoint가 아직 발생하지 않은 상태다.",
      bufferCache: [
        { id: 'A', value: '150', dirty: true, scn: 1000 },
        { id: 'B', value: '250', dirty: true, scn: 1050 },
        { id: 'C', value: '300', dirty: false, scn: null },
      ],
      redoLogBuffer: [],
      dataFiles: [
        { id: 'A', value: '100' },
        { id: 'B', value: '200' },
        { id: 'C', value: '300' },
      ],
      redoLogFiles: [
        { scn: 1000, change: 'A: 100→150' },
        { scn: 1050, change: 'B: 200→250' }
      ],
      checkpointPosition: null,
      activeFlow: null,
      committed: true,
    },
    {
      title: "7단계: Checkpoint 발생!",
      description: "DBWR가 Dirty Buffer들을 Data Files에 기록한다. CKPT가 Checkpoint Position(가장 오래된 Dirty Buffer의 SCN=1000)을 Control File에 기록한다. 이제 메모리와 디스크가 동기화되었다!",
      bufferCache: [
        { id: 'A', value: '150', dirty: false, scn: null },
        { id: 'B', value: '250', dirty: false, scn: null },
        { id: 'C', value: '300', dirty: false, scn: null },
      ],
      redoLogBuffer: [],
      dataFiles: [
        { id: 'A', value: '150' },
        { id: 'B', value: '250' },
        { id: 'C', value: '300' },
      ],
      redoLogFiles: [
        { scn: 1000, change: 'A: 100→150' },
        { scn: 1050, change: 'B: 200→250' }
      ],
      checkpointPosition: 1050,
      activeFlow: 'checkpoint',
      committed: true,
    },
    {
      title: "8단계: Checkpoint 이후 장애 발생하면?",
      description: "이제 장애가 발생해도 Data Files에 이미 최신 데이터가 있다. Checkpoint Position(SCN 1050) 이후의 Redo Log만 적용하면 되는데, 이 경우 적용할 것이 없다. 즉, Recovery 시간 = 0에 가깝다!",
      bufferCache: [
        { id: '?', value: '???', dirty: false, scn: null },
        { id: '?', value: '???', dirty: false, scn: null },
        { id: '?', value: '???', dirty: false, scn: null },
      ],
      dataFiles: [
        { id: 'A', value: '150' },
        { id: 'B', value: '250' },
        { id: 'C', value: '300' },
      ],
      redoLogFiles: [
        { scn: 1000, change: 'A: 100→150', applied: true },
        { scn: 1050, change: 'B: 200→250', applied: true }
      ],
      checkpointPosition: 1050,
      activeFlow: 'recovery',
      crashed: true,
      recovered: true,
    },
  ];

  const currentStep = steps[step];

  const Arrow = ({ direction, active, label }) => (
    <div className={`flex flex-col items-center ${active ? 'text-orange-500' : 'text-gray-400'}`}>
      {label && <span className="text-xs mb-1 font-medium">{label}</span>}
      <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        {direction === 'down' && <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={active ? 3 : 2} d="M19 14l-7 7m0 0l-7-7m7 7V3" />}
        {direction === 'right' && <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={active ? 3 : 2} d="M14 5l7 7m0 0l-7 7m7-7H3" />}
      </svg>
    </div>
  );

  return (
    <div className="min-h-screen bg-gray-900 text-white p-6">
      <h1 className="text-2xl font-bold text-center mb-2 text-blue-400">Oracle Checkpoint 아키텍처</h1>
      <p className="text-center text-gray-400 mb-6">COMMIT vs Checkpoint: 무엇이 어디에 기록되는가</p>
      
      {/* 단계 표시 */}
      <div className="flex justify-center gap-2 mb-6">
        {steps.map((_, idx) => (
          <button
            key={idx}
            onClick={() => setStep(idx)}
            className={`w-8 h-8 rounded-full text-sm font-bold transition-all ${
              idx === step 
                ? 'bg-blue-500 text-white scale-110' 
                : idx < step 
                  ? 'bg-blue-800 text-blue-300' 
                  : 'bg-gray-700 text-gray-400'
            }`}
          >
            {idx + 1}
          </button>
        ))}
      </div>

      {/* 현재 단계 설명 */}
      <div className={`mb-6 p-4 rounded-lg ${currentStep.crashed ? 'bg-red-900/50 border border-red-500' : currentStep.recovered ? 'bg-green-900/50 border border-green-500' : 'bg-gray-800'}`}>
        <h2 className="text-xl font-bold mb-2">{currentStep.title}</h2>
        <p className="text-gray-300">{currentStep.description}</p>
      </div>

      {/* 아키텍처 다이어그램 */}
      <div className="grid grid-cols-2 gap-8">
        {/* 왼쪽: 메모리 (SGA) */}
        <div className="space-y-4">
          <div className={`text-center py-2 rounded-t-lg font-bold ${currentStep.crashed ? 'bg-red-800' : 'bg-blue-800'}`}>
            {currentStep.crashed ? '💥 메모리 (SGA) - 손실됨!' : '🧠 메모리 (SGA)'}
          </div>
          
          {/* Buffer Cache */}
          <div className={`border-2 rounded-lg p-4 ${currentStep.activeFlow === 'update' ? 'border-yellow-500 bg-yellow-900/20' : currentStep.activeFlow === 'checkpoint' ? 'border-green-500 bg-green-900/20' : 'border-blue-600 bg-gray-800'}`}>
            <h3 className="font-bold mb-3 text-blue-300">Database Buffer Cache</h3>
            <div className="space-y-2">
              {currentStep.bufferCache.map((block, idx) => (
                <div 
                  key={idx}
                  className={`flex items-center justify-between p-2 rounded ${
                    block.dirty 
                      ? 'bg-orange-900/50 border border-orange-500' 
                      : currentStep.crashed && block.id === '?'
                        ? 'bg-red-900/50 border border-red-500'
                        : 'bg-gray-700'
                  }`}
                >
                  <span className="font-mono">Block {block.id}</span>
                  <span className="font-bold">{block.value}</span>
                  {block.dirty && (
                    <span className="text-xs bg-orange-600 px-2 py-1 rounded">
                      Dirty (SCN:{block.scn})
                    </span>
                  )}
                </div>
              ))}
            </div>
            {currentStep.bufferCache.some(b => b.dirty) && (
              <p className="text-xs text-orange-400 mt-2">⚠️ Dirty Buffer: 메모리에만 존재, 디스크 미반영</p>
            )}
          </div>

          {/* Redo Log Buffer */}
          <div className={`border-2 rounded-lg p-4 ${currentStep.activeFlow === 'commit' ? 'border-purple-500 bg-purple-900/20' : 'border-blue-600 bg-gray-800'}`}>
            <h3 className="font-bold mb-3 text-blue-300">Redo Log Buffer</h3>
            {currentStep.redoLogBuffer && currentStep.redoLogBuffer.length > 0 ? (
              <div className="space-y-1">
                {currentStep.redoLogBuffer.map((log, idx) => (
                  <div key={idx} className="text-sm font-mono bg-purple-900/50 p-2 rounded">
                    SCN {log.scn}: {log.change}
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-gray-500 text-sm">비어있음</p>
            )}
          </div>

          {/* LGWR 표시 */}
          {currentStep.activeFlow === 'commit' && (
            <div className="flex justify-center">
              <div className="bg-purple-700 px-4 py-2 rounded-lg animate-pulse">
                LGWR: Redo Log Buffer → Redo Log Files
              </div>
            </div>
          )}
        </div>

        {/* 오른쪽: 디스크 */}
        <div className="space-y-4">
          <div className="text-center py-2 rounded-t-lg font-bold bg-green-800">
            💾 디스크 (Database Files)
          </div>
          
          {/* Data Files */}
          <div className={`border-2 rounded-lg p-4 ${currentStep.activeFlow === 'checkpoint' ? 'border-green-500 bg-green-900/20' : 'border-green-600 bg-gray-800'}`}>
            <h3 className="font-bold mb-3 text-green-300">Data Files</h3>
            <div className="space-y-2">
              {currentStep.dataFiles.map((block, idx) => (
                <div 
                  key={idx}
                  className={`flex items-center justify-between p-2 rounded ${
                    currentStep.activeFlow === 'checkpoint' 
                      ? 'bg-green-800/50 border border-green-500' 
                      : 'bg-gray-700'
                  }`}
                >
                  <span className="font-mono">Block {block.id}</span>
                  <span className="font-bold">{block.value}</span>
                </div>
              ))}
            </div>
            {currentStep.checkpointPosition && (
              <p className="text-xs text-green-400 mt-2">✅ Checkpoint Position: SCN {currentStep.checkpointPosition}</p>
            )}
          </div>

          {/* Redo Log Files */}
          <div className={`border-2 rounded-lg p-4 ${currentStep.activeFlow === 'commit' || currentStep.activeFlow === 'recovery' ? 'border-purple-500 bg-purple-900/20' : 'border-green-600 bg-gray-800'}`}>
            <h3 className="font-bold mb-3 text-green-300">Redo Log Files</h3>
            {currentStep.redoLogFiles.length > 0 ? (
              <div className="space-y-1">
                {currentStep.redoLogFiles.map((log, idx) => (
                  <div 
                    key={idx} 
                    className={`text-sm font-mono p-2 rounded ${
                      log.applied 
                        ? 'bg-green-800/50 line-through text-gray-500' 
                        : 'bg-purple-900/50'
                    }`}
                  >
                    SCN {log.scn}: {log.change}
                    {log.applied && <span className="text-green-400 ml-2">(이미 적용됨)</span>}
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-gray-500 text-sm">비어있음</p>
            )}
            <p className="text-xs text-gray-400 mt-2">복구 시 이 로그를 사용하여 Data Files 복원</p>
          </div>

          {/* DBWR 표시 */}
          {currentStep.activeFlow === 'checkpoint' && (
            <div className="flex justify-center">
              <div className="bg-green-700 px-4 py-2 rounded-lg animate-pulse">
                DBWR: Dirty Buffer → Data Files
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 핵심 포인트 */}
      <div className="mt-8 grid grid-cols-2 gap-4">
        <div className={`p-4 rounded-lg ${currentStep.activeFlow === 'commit' ? 'bg-purple-900/50 border-2 border-purple-500' : 'bg-gray-800'}`}>
          <h3 className="font-bold text-purple-400 mb-2">📝 COMMIT이 하는 일</h3>
          <ul className="text-sm space-y-1 text-gray-300">
            <li>• Redo Log Buffer → Redo Log Files (LGWR)</li>
            <li>• 트랜잭션 "완료" 표시</li>
            <li className="text-orange-400">• ⚠️ Data Files는 건드리지 않음!</li>
          </ul>
        </div>
        <div className={`p-4 rounded-lg ${currentStep.activeFlow === 'checkpoint' ? 'bg-green-900/50 border-2 border-green-500' : 'bg-gray-800'}`}>
          <h3 className="font-bold text-green-400 mb-2">✅ Checkpoint가 하는 일</h3>
          <ul className="text-sm space-y-1 text-gray-300">
            <li>• Dirty Buffer → Data Files (DBWR)</li>
            <li>• Checkpoint Position 기록 (CKPT)</li>
            <li className="text-green-400">• ✅ 메모리-디스크 동기화 완료!</li>
          </ul>
        </div>
      </div>

      {/* 네비게이션 */}
      <div className="flex justify-center gap-4 mt-8">
        <button
          onClick={() => setStep(Math.max(0, step - 1))}
          disabled={step === 0}
          className="px-6 py-2 bg-gray-700 rounded-lg disabled:opacity-50 hover:bg-gray-600 transition-colors"
        >
          ← 이전
        </button>
        <button
          onClick={() => setStep(Math.min(steps.length - 1, step + 1))}
          disabled={step === steps.length - 1}
          className="px-6 py-2 bg-blue-600 rounded-lg disabled:opacity-50 hover:bg-blue-500 transition-colors"
        >
          다음 →
        </button>
      </div>

      {/* 단계 요약 */}
      <div className="mt-6 text-center text-sm text-gray-500">
        {step + 1} / {steps.length} 단계
      </div>
    </div>
  );
};

export default CheckpointArchitecture;
