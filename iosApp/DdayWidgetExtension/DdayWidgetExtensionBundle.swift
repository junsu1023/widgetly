//
//  DdayWidgetExtensionBundle.swift
//  DdayWidgetExtension
//
//  Created by 김준수 on 10/5/26.
//

import WidgetKit
import SwiftUI

@main
struct DdayWidgetExtensionBundle: WidgetBundle {
    var body: some Widget {
        DdayWidgetExtension()
        ConnectionWidget()
    }
}
